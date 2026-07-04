# Oncall

Sistema de gestión de guardias y turnos. Permite administrar aplicaciones, personas, grupos de guardia y turnos, con soporte para intercambio parcial de horas entre personas y cálculo de liquidaciones mensuales.

---

## Stack tecnológico

| Componente | Versión |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.0 |
| Maven | Wrapper (`./mvnw`) |
| Base de datos | PostgreSQL |
| Migraciones | Flyway |
| ORM | Spring Data JPA + Hibernate |
| Validation | Jakarta Validation |
| Testing | JUnit 5 + Mockito + Testcontainers + Spring Boot Test |

---

## Requisitos previos

- **Java 25** — la JDK debe estar instalada y `JAVA_HOME` configurada.
- **Maven** — se usa el wrapper incluido (`./mvnw`), no requiere instalación global.
- **PostgreSQL** — necesario para ejecución local. Para tests se usa Testcontainers (Docker).
- **Docker** — necesario para los tests de integración con Testcontainers.

---

## Estructura del proyecto

```
src/main/java/com/nextdigital/oncall/
├── config/          → Beans, configuración, security
├── controller/      → REST controllers
├── dto/             → Request/Response DTOs
├── entity/          → JPA entities
├── exception/       → Global exception handlers
├── mapper/          → Entity ↔ DTO converters
├── repository/      → Spring Data JPA repositories
├── service/         → Business logic (interface + impl)
├── telegram/        → Integración Telegram (bot, comandos, notificaciones)
└── zadarma/         → Integración Zadarma (redirección VoIP, scheduler)

src/main/resources/
├── application.yml           → Configuración base
├── application-dev.yml       → Perfil de desarrollo
├── application-prod.yml      → Perfil de producción
└── db/migration/             → Migraciones Flyway
```

### Arquitectura por capas

Reglas estrictas de dependencia:

```
Controller → Service → Repository → Entity
                ↕
              Mapper ↔ DTO
```

- `Controller` depende de `Service` y `DTO`
- `Service` depende de `Repository`, `Entity`, `Mapper` y `DTO`
- `Repository` depende de `Entity`
- `Entity` no depende de ninguna capa interna
- `DTO` nunca depende de `Entity` (no exponer entidades en la API)

---

## Arranque rápido

### Perfiles disponibles

| Perfil | Uso | Datasource |
|---|---|---|
| `dev` | Desarrollo local | Variables de entorno con fallback a localhost |
| `prod` | Producción | Variables de entorno estrictas (sin fallback) |

### Desarrollo local

```bash
# Arrancar con perfil dev (conexión por defecto a localhost:5432/oncall)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Arrancar con credenciales personalizadas
DB_URL=jdbc:postgresql://localhost:5432/oncall \
DB_USER=postgres \
DB_PASSWORD=postgres \
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Producción

```bash
# Las variables de entorno son obligatorias (sin fallback)
DB_URL=jdbc:postgresql://prod-db:5432/oncall \
DB_USER=prod_user \
DB_PASSWORD=s3cr3t \
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

Si no se especifica ningún perfil, solo se carga `application.yml` (útil para entornos donde Testcontainers u otro mecanismo provee la conexión a BD).

---

## Variables de entorno

| Variable | Perfil | Obligatoria | Default | Descripción |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | — | No | — | Perfil activo de Spring (`dev`, `prod`) |
| `DB_URL` | `dev` | No | `jdbc:postgresql://localhost:5432/oncall` | URL de conexión a PostgreSQL |
| `DB_URL` | `prod` | **Sí** | — | URL de conexión a PostgreSQL |
| `DB_USER` | `dev` | No | `postgres` | Usuario de base de datos |
| `DB_USER` | `prod` | **Sí** | — | Usuario de base de datos |
| `DB_PASSWORD` | `dev` | No | `postgres` | Contraseña de base de datos |
| `DB_PASSWORD` | `prod` | **Sí** | — | Contraseña de base de datos |
| `GOOGLE_CLIENT_ID` | todos | No | `test-client-id` | Client ID de Google OAuth 2.0 (consola GCP) |
| `GOOGLE_CLIENT_SECRET` | `dev` | **Sí** (Swagger UI OAuth) | — | Client Secret de Google OAuth 2.0 (consola GCP) |
| `TELEGRAM_WEBHOOK_URL` | todos | No | — | URL base del webhook de Telegram (opcional, si se usa webhook en vez de polling) |
| `ZADARMA_API_KEY` | todos | No | — | API key de Zadarma (para redirección VoIP) |
| `ZADARMA_API_SECRET` | todos | No | — | API secret de Zadarma (para redirección VoIP) |

### Notas

- **Perfil `dev`**: todas las variables de base de datos tienen valores por defecto para desarrollo local. Las variables `GOOGLE_CLIENT_ID`, `ZADARMA_API_KEY`, `ZADARMA_API_SECRET` y `TELEGRAM_WEBHOOK_URL` son opcionales según las funcionalidades que se quieran probar.
- **Perfil `prod`**: las variables de base de datos (`DB_URL`, `DB_USER`, `DB_PASSWORD`) **no tienen fallback** y deben definirse obligatoriamente.
- **Google OAuth2**: `GOOGLE_CLIENT_ID` se utiliza tanto para la validación de JWT en el resource server como para la configuración OAuth2 de Swagger UI. En el perfil `dev` se usa un client-id real de desarrollo; en producción debe reemplazarse por el de la aplicación desplegada.
- **Telegram**: el token del bot se configura por grupo de guardia (campo `telegramBotToken` en la entidad `OnCallGroup`), no como variable de entorno global. `TELEGRAM_WEBHOOK_URL` solo es necesaria si se utiliza el modo webhook en lugar del polling (por defecto se usa polling).
- **Zadarma**: si no se configuran `ZADARMA_API_KEY` y `ZADARMA_API_SECRET`, la redirección VoIP simplemente queda deshabilitada.

---

## Base de datos

### Migraciones Flyway

Las migraciones están en `src/main/resources/db/migration/` y se aplican automáticamente al arrancar.

```bash
# Estado de las migraciones
./mvnw flyway:info

# Aplicar migraciones pendientes
./mvnw flyway:migrate
```

Hibernate opera con `ddl-auto=validate`: el esquema lo gestiona exclusivamente Flyway, Hibernate solo valida que las entidades coincidan con el esquema existente.

### Esquema actual (V1 + V2)

| Tabla | Descripción |
|---|---|
| `applications` | Catálogo de aplicaciones |
| `people` | Personas participantes (soporta soft-delete vía `is_active`) |
| `on_call_groups` | Grupos de guardia con precio bruto por día |
| `on_call_group_apps` | Relación ManyToMany grupos ↔ aplicaciones |
| `on_call_group_people` | Relación ManyToMany grupos ↔ personas |
| `shifts` | Turnos reales con rango de tiempo y CHECK `start_time < end_time` |
| `on_call_group_price_history` | Historial de precios de grupos con rangos de vigencia (`effective_from`/`effective_to`) |

> **V2**: La migración `V2__add_on_call_group_price_history.sql` añade la tabla de historial de precios
> y migra automáticamente los precios actuales de cada grupo como su primer registro histórico.
>
> **V3**: Integración Telegram — columnas en `people` y `on_call_groups`, tabla `telegram_pending_links`.
>
> **V6**: Integración Zadarma — `zadarma_sip_id` y `zadarma_redirection_type` en `on_call_groups`; `redirect_status` y trazabilidad de reintentos en `shifts`.

---

## API REST

Base URL: `/api/v1/`

### Applications

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/v1/applications` | Listar todas |
| `GET` | `/api/v1/applications/{id}` | Obtener por ID |
| `POST` | `/api/v1/applications` | Crear |
| `PUT` | `/api/v1/applications/{id}` | Actualizar |
| `DELETE` | `/api/v1/applications/{id}` | Eliminar |

### People

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/v1/people` | Listar activos (añadir `?includeInactive=true` para todos) |
| `GET` | `/api/v1/people/{id}` | Obtener por ID |
| `POST` | `/api/v1/people` | Crear |
| `PUT` | `/api/v1/people/{id}` | Actualizar |
| `DELETE` | `/api/v1/people/{id}` | Soft-delete (pone `isActive=false`) |

### OnCallGroups

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/v1/on-call-groups` | Listar todos |
| `GET` | `/api/v1/on-call-groups/{id}` | Obtener por ID (con apps y personas anidadas) |
| `POST` | `/api/v1/on-call-groups` | Crear (enviar `applicationIds` y `personIds`) |
| `PUT` | `/api/v1/on-call-groups/{id}` | Actualizar |
| `DELETE` | `/api/v1/on-call-groups/{id}` | Eliminar |

### Shifts

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/v1/shifts` | Listar turnos con filtros opcionales (`personId`, `onCallGroupId`, `from`, `to`) |
| `GET` | `/api/v1/shifts/{id}` | Obtener por ID |
| `POST` | `/api/v1/shifts` | Crear turno (valida solapamientos y divide turnos de otras personas automáticamente) |
| `PUT` | `/api/v1/shifts/{id}` | Actualizar turno (ajusta turnos adyacentes de la misma persona) |
| `DELETE` | `/api/v1/shifts/{id}` | Eliminar turno |

### Billing / Facturación

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/v1/billing` | Calcular facturación con filtros (`groupId` obligatorio, `personId`, `from`, `to` opcionales) |

**Parámetros:**
- `groupId` (obligatorio) — ID del grupo de guardia
- `personId` (opcional) — filtrar por persona específica
- `from` (opcional, ISO 8601) — inicio del período. Por defecto: inicio del mes actual
- `to` (opcional, ISO 8601) — fin del período. Por defecto: fin del mes actual

**Cálculo:**
1. Obtiene todos los turnos del grupo en el rango de fechas
2. Para cada turno, busca el precio histórico vigente en `shift.startTime` en la tabla `on_call_group_price_history`
3. Si no hay historial, usa el `gross_price_per_day` actual del grupo
4. Calcula: `horas → días (horas / 24) → importe (días × precio)`
5. Agrupa por persona y suma totales del reporte

**Respuesta:**
```json
{
  "data": {
    "groupId": "uuid",
    "groupName": "SRE OnCall",
    "grossPricePerDay": "100.00",
    "from": "2026-06-01T00:00:00Z",
    "to": "2026-06-30T23:59:59Z",
    "totalHours": "36.0000",
    "totalBilling": "150.00",
    "details": [
      {
        "personId": "uuid",
        "personName": "Alice",
        "personEmail": "alice@example.com",
        "totalHours": "24.0000",
        "totalDays": "1.0000",
        "grossPricePerDay": "100.00",
        "totalBilling": "100.00"
      }
    ]
  },
  "errors": null
}
```

**Comportamiento de creación:**
- Si la misma persona ya tiene un turno en el rango → **409 Conflict**
- Si otra persona tiene un turno en el rango → se divide automáticamente (tramos delantero y trasero)

**Comportamiento de actualización:**
- Si se extiende el turno → el siguiente turno de la misma persona se acorta
- Si se acorta el turno → el siguiente turno se extiende para rellenar el hueco
- Si se mueve el inicio → el turno anterior se ajusta análogamente

### Zadarma — Redirección de llamadas VoIP

> 📚 Documentación completa: [docs/zadarma.md](docs/zadarma.md)

Al crear un turno, el sistema programa automáticamente la redirección de llamadas al teléfono de la persona cuando llegue la hora de inicio. Si la redirección falla, se reintenta con backoff y se alerta por Telegram.

**Solo se necesita 1 llamada a la API de Zadarma** (`PUT /v1/sip/redirection/`), no el antiguo flujo de 3 llamadas.

| Fase | Comportamiento |
|---|---|
| Crear turno futuro | Se guarda en BD, se programa la redirección para la hora de inicio. No se llama a Zadarma todavía. |
| Hora de inicio | El scheduler ejecuta `PUT /v1/sip/redirection/` → el grupo redirige al teléfono de la persona. |
| Hora de fin | Busca el siguiente turno activo y redirige a esa persona, o desactiva la redirección si no hay nadie. |
| Fallo de API | El turno se marca como `failed`, se preserva la redirección anterior, se reintenta con backoff (30s → 60s → ... ×10), se alerta por Telegram. |
| Reinicio del servidor | Recupera todos los turnos activos/futuros de la BD y reprograma sus transiciones. |
| Fallback (cada 5 min) | Verifica que todos los turnos activos tengan la redirección correcta; corrige desviaciones. |

**Configuración:**
```bash
export ZADARMA_API_KEY=your_key
export ZADARMA_API_SECRET=your_secret
```

Para que un grupo use redirección, asignarle `zadarmaSipId`:
```json
POST /api/v1/on-call-groups
{ "name": "Crew OnCall", "zadarmaSipId": "921495", "zadarmaRedirectionType": "sip" }
```

**Columnas en BD:**
- `on_call_groups.zadarma_sip_id` — ID del SIP en Zadarma (null = sin redirección)
- `on_call_groups.zadarma_redirection_type` — `sip` (por defecto) o `pbx` (futuro)
- `shifts.redirect_status` — `null` (pendiente), `ok`, `failed`
- `shifts.redirect_failed_at`, `redirect_last_attempt_at`, `redirect_retry_count` — trazabilidad de reintentos

### Documentación interactiva (Swagger / OpenAPI)

La API dispone de documentación interactiva generada automáticamente con **SpringDoc OpenAPI**.

| Recurso | URL |
|---|---|
| Swagger UI | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| Especificación OpenAPI (JSON) | [http://localhost:8080/api-docs](http://localhost:8080/api-docs) |

La Swagger UI permite explorar todos los endpoints, ver esquemas de entrada/salida, códigos de respuesta esperados y ejecutar peticiones directamente desde el navegador.

### Formato de respuesta

Todas las respuestas siguen el formato envolvente:

```json
// Éxito
{ "data": { ... }, "errors": null }

// Error
{ "data": null, "errors": [{ "code": "NOT_FOUND", "message": "...", "field": null }] }
```

---

## Tests

### Unitarios

48 tests con JUnit 5 + Mockito que cubren los cinco servicios:

- `ApplicationServiceImplTest` (8 tests)
- `PersonServiceImplTest` (8 tests)
- `OnCallGroupServiceImplTest` (8 tests)
- `ShiftServiceImplTest` (17 tests)
- `BillingServiceImplTest` (8 tests)

```bash
./mvnw test
```

### Integración

19 tests con Spring Boot Test + Testcontainers (PostgreSQL real en Docker):

- `ApplicationIntegrationTest` (3 tests)
- `PersonIntegrationTest` (3 tests)
- `OnCallGroupIntegrationTest` (3 tests)
- `ShiftIntegrationTest` (5 tests)
- `BillingIntegrationTest` (5 tests)

```bash
./mvnw verify
```

Los tests de integración:
- Levantan un contenedor PostgreSQL 14 automáticamente
- Ejecutan Flyway para crear el esquema
- Validan que Hibernate `ddl-auto=validate` pase contra el esquema
- Realizan peticiones HTTP reales vía MockMvc contra cada endpoint

### Comandos útiles

```bash
./mvnw clean install         # Build sin tests
./mvnw clean verify          # Build con todos los tests
./mvnw test                  # Solo tests
./mvnw test -Dtest="ApplicationServiceImplTest"  # Test específico
```

---

## Docker

### docker-compose — Base de datos local

```bash
docker compose up -d
```

Arranca PostgreSQL 17 en `localhost:5432` con usuario/contraseña `postgres` y base de datos `oncall`. Los datos persisten en un volumen Docker (`pgdata`).

Para detenerlo: `docker compose down`

### Dockerfile — Imagen de la aplicación

Construcción multi-stage (build → runtime):

```bash
docker build -t oncall .
```

- **Stage 1 (build)**: imagen `maven:3.9-eclipse-temurin-25`, descarga dependencias y ejecuta `./mvnw clean package -DskipTests`
- **Stage 2 (runtime)**: imagen `eclipse-temurin:25-jre`, copia solo el `.jar` (~30 MB) y expone el puerto `8080`

### Ejecutar el contenedor

Conectándose al PostgreSQL del host (o del `docker compose`):

```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e DB_URL=jdbc:postgresql://localhost:5432/oncall \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  oncall
```

Si la BD está en otro contenedor Docker, usar una red compartida:

```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e DB_URL=jdbc:postgresql://oncall-postgres:5432/oncall \
  --network oncall_default \
  oncall
```

---

## Comandos Maven de uso frecuente

```bash
./mvnw clean compile         # Compilar
./mvnw clean install         # Build completo (sin tests)
./mvnw clean verify          # Build completo (con tests)
./mvnw spring-boot:run       # Arrancar aplicación
./mvnw flyway:migrate        # Aplicar migraciones Flyway
./mvnw flyway:info           # Ver estado de migraciones
```

---

## Documentación adicional

- `docs/architecture.md` — Detalle de la arquitectura en capas
- `docs/database.md` — Convenciones de base de datos y Flyway
- `docs/api-guidelines.md` — Guía de diseño de API REST
- `docs/adr/` — Architecture Decision Records
- `TASKS.md` — Roadmap y estado del proyecto
