# ADR-004: Sistema de roles y control de acceso — `ADMIN`, `RRHH` y miembros de guardia

**Estado**: Propuesto
**Fecha**: 2026-09-09

## Contexto

Hoy el único control de acceso es la autenticación: cualquier cuenta Google del dominio `@nextdigital.es` puede ver y modificar todos los recursos. ADR-003 dejó pendiente explícitamente restringir la consulta de auditoría "cuando exista modelo de roles".

Se necesitan tres niveles de acceso:

1. **RRHH**: solo ve los temas de facturación.
2. **Miembro de guardia**: solo ve lo relacionado con su(s) grupo(s) de guardia.
3. **Administrador**: control total (puede pertenecer o no a un grupo).

Requisitos adicionales:

- El rol debe servir también para **restringir el frontal** (ocultar/mostrar menús y acciones).
- El backend debe **imponer** las restricciones; el frontal solo oculta opciones (nunca es la barrera de seguridad).
- No debe bloquearse el sistema por falta de administradores (bootstrap inicial).
- La pertenencia a grupos ya existe (`on_call_group_people`, many-to-many `Person` ↔ `OnCallGroup`) y se reutiliza tal cual.

## Decisión

### 1. Modelo de datos: enum `role` en `people` + pertenencia a grupos existente

- **Un único rol global** por persona, columna nueva en `people`:
  - `ADMIN`: control total sobre todos los recursos.
  - `RRHH`: solo facturación (todos los grupos).
  - `USER` (default): acceso limitado a sus grupos de guardia.
- El **alcance por grupo** no es un rol: se deriva de `on_call_group_people`. Un `USER` ve los recursos de los grupos a los que pertenece; un `ADMIN` ve todos; `RRHH` no necesita pertenecer a grupos (solo facturación).
- `ADMIN` es jerárquicamente superior: implica todo lo que pueden hacer `RRHH` y `USER` (evita conflictos si un admin además es RRHH o pertenece a grupos).
- El rol se guarda en BD, **nunca** en el JWT de Google (Google no conoce los roles de la aplicación).

**Migración** `V12__add_person_role.sql` (Flyway, inmutable):

```sql
ALTER TABLE people ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE people ADD CONSTRAINT chk_people_role CHECK (role IN ('ADMIN', 'RRHH', 'USER'));
CREATE INDEX idx_people_role ON people (role);

-- Bootstrap: el primer administrador se asigna por configuración al arrancar
-- (ver apartado 5), no en la migración.
```

**Entidad**: enum `PersonRole { ADMIN, RRHH, USER }` en `entity/`, campo `role` en `Person` con `@Enumerated(EnumType.STRING)` y `@ColumnDefault`/default `USER`.

### 2. Autorización en backend: method security + bean de control de acceso

- Activar `@EnableMethodSecurity` en `SecurityConfig`.
- Nuevo componente **`AccessControlService`** (paquete `security/`) que resuelve al usuario actual y responde preguntas de autorización:
  - `currentPerson()`: `Person` del contexto (atributo de petición publicado por `PersonSyncFilter`, fallback por `email` del JWT).
  - `isAdmin()`, `isHr()`.
  - `canAccessGroup(UUID groupId)`: `ADMIN` → `true`; si no, `groupId ∈ person.onCallGroups`.
  - `visibleGroupIds()`: grupos que el usuario puede ver (`ADMIN` → todos, resto → los suyos). Los servicios lo usan para **filtrar listados**.
- **Reglas gruesas por URL** en `SecurityConfig` (rápidas, para cortar categorías enteras):

```java
.requestMatchers("/api/v1/billing/**").hasAnyRole("ADMIN", "RRHH")
.requestMatchers("/api/v1/audit/**").hasRole("ADMIN")
.requestMatchers("/api/v1/me").authenticated()
```

- **Reglas finas por método** con SpEL sobre el bean, en controladores/servicios:
  `@PreAuthorize("@accessControl.canAccessGroup(#groupId)")` para turnos, grupos y personas.
- **Filtrado de datos en el servicio**: cuando un `USER` lista turnos/grupos/personas, el servicio intersecta con `visibleGroupIds()`; si accede directamente a un recurso fuera de su alcance → `403 FORBIDDEN`.
- **Autoridades en el contexto**: `PersonSyncFilter` (que ya resuelve la `Person` por petición) pasa a construir además las `GrantedAuthority`s (`ROLE_ADMIN`, `ROLE_RRHH`, `ROLE_USER` y `GROUP_<uuid>` por cada grupo) y reemplaza el `Authentication` con un `JwtAuthenticationToken` que las lleva. Un solo acceso a BD por petición; el atributo `oncall.syncedPerson` sigue disponible para la auditoría.

### 3. Matriz de permisos

| Recurso | ADMIN | RRHH | Miembro (`USER`) |
|---|---|---|---|
| `GET /api/v1/billing` | ✅ todos los grupos | ✅ todos los grupos | ❌ |
| `GET /api/v1/shifts` (turnos) | ✅ todos | ❌ | ✅ solo sus grupos (lectura) |
| `POST/PUT/DELETE /api/v1/shifts` | ✅ | ❌ | ❌ *(v1: solo lectura)* |
| `GET /api/v1/on-call-groups` (grupos) | ✅ todos | ❌ | ✅ solo sus grupos (lectura) |
| `POST/PUT/DELETE /api/v1/on-call-groups` | ✅ | ❌ | ❌ |
| `GET /api/v1/people` | ✅ todos | ❌ | ✅ solo personas de sus grupos |
| `POST/PUT/DELETE /api/v1/people` | ✅ | ❌ | ❌ |
| `PATCH /api/v1/people/{id}/role` | ✅ | ❌ | ❌ |
| `GET /api/v1/applications` | ✅ | ❌ | ✅ (lectura) |
| `GET /api/v1/audit` | ✅ | ❌ *(cierra ADR-003)* | ❌ |
| `/api/v1/telegram/**`, `/api/v1/zadarma/**` (gestión) | ✅ | ❌ | ❌ |
| `GET /api/v1/me` | ✅ | ✅ | ✅ |

### 4. Contrato con el frontal

- Nuevo endpoint **`GET /api/v1/me`** → `MeResponse`: `personId`, `name`, `email`, `role`, `groupIds` y mapa de capacidades (`canManageBilling`, `canViewAudit`, `canManageAll`, …). El frontal lo consulta al arrancar y oculta/muestra menús y acciones.
- `PersonResponse` incluye `role` para que un `ADMIN` pueda ver y asignar roles desde la pantalla de personas.
- El frontal **no es fuente de autoridad**: toda restricción se re-aplica en backend.

### 5. Gestión de roles y bootstrap

- `PATCH /api/v1/people/{id}/role` con `RoleUpdateRequest { role }`, **solo ADMIN**.
- Bootstrap anti-bloqueo: propiedad `app.security.bootstrap-admin-email` (default: sin valor). Al arrancar, un runner promueve a `ADMIN` a la persona con ese email si existe. Sin esta persona, no se hace nada.
- Los cambios de rol quedan **auditados automáticamente** por los listeners de Hibernate de ADR-003 (la columna `role` forma parte de la fila `people`).

### 6. Telegram

El bot autentica por `telegramUserId` (no por JWT). Los comandos resuelven la `Person` y aplican las mismas reglas a través de `AccessControlService`: un miembro solo consulta la guardia de sus grupos; un admin, todo. La configuración del bot sigue siendo solo de admins.

## Consecuencias

- **Positivas**:
  - Modelo mínimo y entendible: 1 columna + pertenencia a grupos ya existente. Sin tablas nuevas de roles.
  - El frontal recibe un contrato estable (`/api/v1/me`) para renderizar según permisos.
  - Backend impone las reglas en dos capas (URL para lo grueso, SpEL/servicio para lo fino), imposible saltarse por la UI.
  - ADR-003 queda cerrado: la auditoría pasa a ser exclusiva de `ADMIN`.
  - Bootstrap por configuración evita quedarse sin administradores.
- **Negativas**:
  - Un único rol por persona no permite combinar `RRHH` + gestión de un grupo concreto (si apareciera ese caso). Migrar a una tabla `person_roles` many-to-many es el camino de escape previsto.
  - `v1` deja a los miembros en modo solo-lectura de su guardia; si se necesita que editen turnos de su grupo, habrá que añadir esa regla y revisar este ADR.
  - Una consulta extra a BD por petición para resolver la `Person` (ya existía en `PersonSyncFilter`; no empeora). Si duele, se cachea.

## Alternativas consideradas

| Alternativa | Rechazada por |
|---|---|
| Tabla `person_roles` (muchos roles por persona) | Complejidad innecesaria hoy: los tres niveles no se combinan entre sí. Se deja como evolución prevista |
| Roles codificados en el JWT de Google | Google no gestiona roles de la app; delegaría el control a un administrador externo |
| Solo restricción en el frontal | Inseguro: cualquier llamada directa a la API la elude |
| AOP propio en servicios sin Spring Security | Reinventa `@PreAuthorize`; más código y menos estándar |
| Reescribir `PersonSyncFilter` con un `JwtAuthenticationConverter` completo | Más invasivo: el filtro ya resuelve la `Person` y publica el atributo que usa la auditoría; se aprovecha ese punto único |
