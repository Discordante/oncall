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
└── service/         → Business logic (interface + impl)

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

### Esquema actual (V1)

| Tabla | Descripción |
|---|---|
| `applications` | Catálogo de aplicaciones |
| `people` | Personas participantes (soporta soft-delete vía `is_active`) |
| `on_call_groups` | Grupos de guardia con precio bruto por día |
| `on_call_group_apps` | Relación ManyToMany grupos ↔ aplicaciones |
| `on_call_group_people` | Relación ManyToMany grupos ↔ personas |
| `shifts` | Turnos reales con rango de tiempo y CHECK `start_time < end_time` |

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

**Comportamiento de creación:**
- Si la misma persona ya tiene un turno en el rango → **409 Conflict**
- Si otra persona tiene un turno en el rango → se divide automáticamente (tramos delantero y trasero)

**Comportamiento de actualización:**
- Si se extiende el turno → el siguiente turno de la misma persona se acorta
- Si se acorta el turno → el siguiente turno se extiende para rellenar el hueco
- Si se mueve el inicio → el turno anterior se ajusta análogamente

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

40 tests con JUnit 5 + Mockito que cubren los cuatro servicios:

- `ApplicationServiceImplTest` (8 tests)
- `PersonServiceImplTest` (8 tests)
- `OnCallGroupServiceImplTest` (8 tests)
- `ShiftServiceImplTest` (17 tests)

```bash
./mvnw test
```

### Integración

14 tests con Spring Boot Test + Testcontainers (PostgreSQL real en Docker):

- `ApplicationIntegrationTest` (3 tests)
- `PersonIntegrationTest` (3 tests)
- `OnCallGroupIntegrationTest` (3 tests)
- `ShiftIntegrationTest` (5 tests)

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
