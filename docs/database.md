# Base de datos

## Tecnología

- **Motor**: PostgreSQL
- **Migraciones**: Flyway
- **ORM**: Hibernate via Spring Data JPA
- **Gestión del esquema**: Flyway (ddl-auto=validate)

## Conexión local

Configuración por defecto en `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/oncall
spring.datasource.username=postgres
spring.datasource.password=postgres
```

## Flyway

### Ubicación de migraciones

`src/main/resources/db/migration/`

### Convención de nomenclatura

```
V<numero>__<descripcion_snake_case>.sql
```

Ejemplos:

| Archivo | Descripción |
|---|---|
| `V1__create_teams_table.sql` | Creación de tabla teams |
| `V2__create_schedules_table.sql` | Creación de tabla schedules |
| `V3__add_rotation_fields.sql` | Añadir columnas de rotación |

### Buenas prácticas

- Las migraciones son **inmutables** — nunca editar una ya publicada
- Si hay que cambiar algo, crear una nueva migración `V<N+1>__...`
- Usar `--rollback` comments para poder deshacer migraciones si es necesario
- Versionado secuencial sin saltos

## Convenciones de esquema

### Nombrado

| Elemento | Convención | Ejemplo |
|---|---|---|
| Tablas | snake_case, plural | `teams`, `schedules`, `rotation_members` |
| Columnas | snake_case | `full_name`, `created_at`, `is_active` |
| PK | `id` (bigserial) | `id BIGSERIAL PRIMARY KEY` |
| FK | `<tabla>_id` | `team_id`, `schedule_id` |
| Timestamps | `created_at`, `updated_at` | `TIMESTAMP WITH TIME ZONE` |

### Columnas obligatorias

Toda tabla debe incluir:

```sql
id          BIGSERIAL PRIMARY KEY,
created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
```

### Índices

- FK siempre indexadas
- Columnas de búsqueda frecuente indexadas
- Índices compuestos cuando las queries lo requieran

## Tests con Testcontainers

En los tests de integración se levanta un PostgreSQL real en contenedor Docker. La conexión se auto-configura via `@ServiceConnection`. No se usa H2 en ningún test.
