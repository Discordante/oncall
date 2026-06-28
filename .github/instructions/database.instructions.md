---
description: "Use when writing Flyway migrations, creating/modifying JPA entities, or adding database schema changes. Covers naming conventions, required columns, and migration best practices."
---

# Database Guidelines

## Technology

- **Engine**: PostgreSQL
- **Migrations**: Flyway
- **ORM**: Hibernate via Spring Data JPA
- **Schema management**: Flyway (`ddl-auto=validate`)

## Migration files

Location: `src/main/resources/db/migration/`

Naming: `V<number>__<description_snake_case>.sql`

Examples:

| File | Description |
|---|---|
| `V1__create_teams_table.sql` | Create teams table |
| `V2__create_schedules_table.sql` | Create schedules table |

## Schema naming conventions

| Element | Convention | Example |
|---|---|---|
| Tables | snake_case, plural | `teams`, `schedules` |
| Columns | snake_case | `full_name`, `created_at` |
| PK | `id` (bigserial) | `id BIGSERIAL PRIMARY KEY` |
| FK | `<table>_id` | `team_id`, `schedule_id` |
| Timestamps | `created_at`, `updated_at` | `TIMESTAMP WITH TIME ZONE` |

## Required columns on every table

```sql
id          BIGSERIAL PRIMARY KEY,
created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
```

## Migration best practices

- Migrations are **immutable** — never edit a published migration
- Create a new `V<N+1>__...` for changes
- Use `--rollback` comments for reversibility
- Sequential versioning, no gaps

## Indexes

- Always index foreign keys
- Index frequently queried columns
- Composite indexes where query patterns require them

## Testing

- Integration tests use Testcontainers with real PostgreSQL (no H2)
- `@ServiceConnection` auto-configures the container connection
- Flyway runs automatically during tests

See [docs/database.md](/docs/database.md) for full details.
