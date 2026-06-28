# Oncall — Project Guidelines

## Stack

| Componente | Versión |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.0 |
| Maven | Wrapper (./mvnw) |
| Base de datos | PostgreSQL |
| Migraciones | Flyway |
| ORM | Spring Data JPA + Hibernate |
| Validation | Jakarta Validation |
| Testing | JUnit 5 + Mockito + Testcontainers + Spring Boot Test |

## Architecture

Traditional layered architecture: Controller → Service → Repository → Entity.

Key rules:
- `controller` depends on `service` and `dto`
- `service` depends on `repository`, `entity`, `dto` and `mapper`
- `repository` depends on `entity`
- `entity` depends on NO internal layers
- `dto` never depends on `entity` — never expose entities in the API

See [architecture instructions](instructions/architecture.instructions.md) and [docs/architecture.md](/docs/architecture.md) for full details.

## Package structure

```
com.nextdigital.oncall
├── config/       → Beans, configuración, security
├── controller/   → REST controllers
├── dto/          → Request/Response DTOs
├── entity/       → JPA entities
├── exception/    → Global exception handlers
├── mapper/       → Entity ↔ DTO converters
├── repository/   → Spring Data JPA repositories
└── service/      → Business logic
```

## Coding conventions

- **Lombok**: `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@Slf4j`
- **DTOs**: suffix `Request` / `Response` (e.g. `TeamRequest`, `TeamResponse`)
- **Entities**: no suffix (e.g. `Team`, `Schedule`)
- **Repositories**: suffix `Repository` (e.g. `TeamRepository`)
- **Services**: interface + impl, suffix `Service` / `ServiceImpl`
- **Validation**: Jakarta Validation on DTOs (`@NotBlank`, `@NotNull`, `@Size`)
- **Exceptions**: `@RestControllerAdvice` with `@ExceptionHandler`

## Build & test commands

```bash
./mvnw clean install        # Build sin tests
./mvnw clean verify         # Build con tests
./mvnw spring-boot:run      # Run dev
./mvnw test                 # Solo tests
./mvnw flyway:migrate       # Aplicar migraciones
./mvnw flyway:info          # Estado migraciones
```

## Testing strategy

- **Unit**: JUnit 5 + Mockito for services
- **Slice tests**: `@WebMvcTest` (controllers), `@DataJpaTest` (repos)
- **Integration**: `@SpringBootTest` + Testcontainers with real PostgreSQL
- Flyway runs automatically in integration tests
- `@ServiceConnection` for automatic Testcontainers setup

## Database

- Flyway migrations in `src/main/resources/db/migration/`
- Naming: `V<number>__<description>.sql`
- `ddl-auto=validate` — Hibernate only validates, Flyway manages schema
- See [database instructions](instructions/database.instructions.md) for schema conventions

## API design

- Base path: `/api/v1/<resource>`
- Standard REST methods (GET, POST, PUT, PATCH, DELETE)
- Wrapped responses: `{ data: ..., errors: ... }`
- ISO 8601 dates
- See [api instructions](instructions/api.instructions.md) for full guidelines
