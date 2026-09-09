# ADR-003: Auditoría de acciones — tabla única `audit_logs` con listeners de eventos de Hibernate

**Estado**: Aceptado
**Fecha**: 2026-08-13

## Contexto

El servicio va a ser utilizado por mucha gente, por lo que es necesario un registro íntegro de **quién hace qué** para evitar malentendidos y poder resolver disputas con evidencia.

Todos los usuarios interactúan autenticados con JWT de Google (`spring-boot-starter-oauth2-resource-server`), de modo que el actor de cada acción es siempre identificable (`email`, `name`, `sub` → `Person` sincronizada por `PersonSyncFilter`).

Requisitos:

1. Registrar **todas las acciones de escritura** (crear, modificar, eliminar) con su autor.
2. **Una única tabla de auditoría**, sencilla y práctica, preparada para una pantalla con filtros.
3. Solución **sólida, fiable y estándar** — sin dependencias nuevas ni esquema replicado por entidad.
4. Políticas **fácilmente modificables** por configuración.

## Decisión

### 1. Motor de auditoría: listeners de eventos de Hibernate sobre una tabla única

- **Una sola tabla `audit_logs`** registra todas las acciones de escritura del servicio.
- El componente `AuditEventListener` implementa los listeners nativos de Hibernate `PostInsertEventListener`, `PostUpdateEventListener` y `PostDeleteEventListener`, y se registra en la `SessionFactory` mediante `HibernatePropertiesCustomizer` (`AvailableSettings.EVENT_LISTENERS`). No se añade ninguna dependencia.
- Los listeners se ejecutan dentro del **mismo flush y la misma transacción** que el cambio de negocio: atómico por construcción. Si falla el registro, toda la transacción hace rollback (fail-closed) — no puede existir un cambio sin registro.
- El diff campo a campo se obtiene automáticamente de `oldState`/`state` de Hibernate y se guarda en JSONB:
  - `CREATE` / `DELETE`: snapshot completo de la fila.
  - `UPDATE`: solo los campos que han cambiado, con valor anterior y nuevo.
- Los cambios en colecciones many-to-many (`on_call_group_people`, `on_call_group_apps`) se capturan con los listeners de colección (`PostCollectionRecreate/Update/Remove`) y se registran como `UPDATE` de la entidad propietaria indicando la colección afectada y los elementos añadidos/eliminados.
- Las lecturas (GET) **no** se auditan.
- La auditoría es **append-only**: no hay endpoints de escritura ni borrado; la única eliminación es la purga por retención.

### 2. Esquema de la tabla única

```sql
CREATE TABLE audit_logs (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    action       VARCHAR(10) NOT NULL CHECK (action IN ('CREATE','UPDATE','DELETE')),
    entity_type  VARCHAR(255) NOT NULL,   -- nombre de tabla de la entidad
    entity_id    VARCHAR(64)  NOT NULL,   -- id de la fila (UUID como texto)
    changes      JSONB,                   -- diff campo a campo / snapshot
    person_id    UUID REFERENCES people(id), -- actor (NULL = SYSTEM)
    actor_email  VARCHAR(255),            -- snapshot denormalizado
    actor_name   VARCHAR(255),
    http_method  VARCHAR(10),
    request_path VARCHAR(500)
);

CREATE INDEX idx_audit_logs_entity      ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_logs_person      ON audit_logs (person_id);
CREATE INDEX idx_audit_logs_changes     ON audit_logs USING GIN (changes);
```

- Tabla append-only: no lleva `updated_at` (desviación documentada de la convención de columnas obligatorias).
- El esquema se crea en la migración Flyway `V10__create_audit_tables.sql` (con `ddl-auto=validate`, el esquema solo lo gestiona Flyway).
- **Actualización (2026-09-09)**: la migración `V11__drop_audit_ip_user_agent.sql` elimina `ip_address` y `user_agent`; esos metadatos HTTP dejan de registrarse para simplificar el log.

### 3. Actor desde el JWT

- `AuditActorProvider` (paquete `audit/`) lee `SecurityContextHolder`:
  - Si hay `JwtAuthenticationToken` → `email`/`name`/`sub` del token y la `Person` resuelta (el `PersonSyncFilter` publica la `Person` sincronizada en un atributo de la petición para evitar consultas extra; fallback a `PersonRepository.findByEmail`).
  - Si no hay contexto autenticado (bot de Telegram, schedulers) → actor `SYSTEM` con columnas de actor a `NULL`.
- Metadatos HTTP (`http_method`, `request_path`) desde `RequestContextHolder`, nulo-seguro para contextos sin petición.

### 4. Configuración centralizada (`app.audit.*`)

Todas las políticas quedan en propiedades de configuración, nunca hardcodeadas:

```yaml
app:
  audit:
    enabled: true                    # Interruptor global del motor de auditoría
    excluded-entities:               # Nombres de tabla excluidos de la auditoría
      - telegram_pending_links       # contiene códigos secretos
    retention-months: 12             # Retención del histórico (decisión temporal: 1 año)
    retention-cron: "0 0 4 * * *"    # Ejecución diaria de la purga (04:00)
    log-reads: false                 # Las lecturas GET no se auditan (reservada para el futuro)
```

- Clase `AuditProperties` con `@ConfigurationProperties(prefix = "app.audit")` en `config/`: `enabled` (default `true`), `excludedEntities` (default `[telegram_pending_links]`), `retentionMonths` (default `12`), `retentionCron` (default `"0 0 4 * * *"`), `logReads` (default `false`, sin comportamiento en v1).
- La purga por retención es un job `@Scheduled` que borra filas con antigüedad superior a `retentionMonths`. Nunca se borran registros de auditoría a mano ni desde la API.

### 5. Acceso a la consulta (decisión temporal)

- **Cualquier usuario autenticado** puede consultar la auditoría (hoy no existe modelo de roles).
- Cuando exista modelo de roles, este ADR se revisará para restringir el acceso (p. ej. rol administrador).

## Consecuencias

- **Positivas**:
  - **Una sola tabla** → consultas y pantalla de filtros triviales (Specification + paginación).
  - Sin dependencias nuevas y sin tablas `*_aud` replicadas por entidad.
  - Registro atómico, completo y con autor en cada cambio → evidencia ante malentendidos.
  - Políticas (activación, entidades excluidas, retención) modificables por configuración sin tocar código.
- **Negativas**:
  - No hay snapshot "entidad tal y como estaba en el instante N" consultable directamente (reconstruible a partir de la secuencia de diffs si hiciera falta).
  - Los cambios de colecciones many-to-many requieren listeners adicionales (más código que el caso simple).
  - Los cambios hechos por SQL manual (p. ej. migraciones) no se auditan — igual que con cualquier solución a nivel ORM.
  - No hay auditoría de lecturas GET (decisión consciente; la propiedad `log-reads` queda reservada).

## Alternativas consideradas

| Alternativa | Rechazada por |
|---|---|
| Hibernate Envers | Genera una tabla `_aud` por entidad auditada — contrario al requisito de **una única tabla sencilla**. Queda como opción futura si se necesitara versionado completo por entidad |
| Eventos Spring + `@TransactionalEventListener(AFTER_COMMIT)` | No atómico: si falla el listener, el negocio ya está commiteado → pérdida de auditoría |
| AOP sobre métodos de servicio | Requiere anotar cada operación manualmente y no captura los campos cambiados; fácil olvidar acciones |
| Triggers de PostgreSQL | Fuera del control de Flyway/Hibernate; difíciles de versionar y testear |
| Javers | Paradigma distinto; integración con JPA y UI más compleja de lo necesario |
| Solo logs de aplicación | Sin estructura consultable ni garantías transaccionales |
