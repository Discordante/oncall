# ADR-001: Flyway + Testcontainers para gestión de base de datos

**Estado**: Aceptado
**Fecha**: 2026-06-23

## Contexto

El proyecto necesita un enfoque consistente para:
- Gestión de cambios en el esquema de base de datos
- Tests que verifiquen el comportamiento real contra PostgreSQL
- Evitar discrepancias entre entorno de desarrollo, CI y producción

## Decisión

Se adopta:

1. **Flyway** como herramienta de migraciones — las migraciones van en `src/main/resources/db/migration/` con nomenclatura `V{numero}__{descripcion}.sql`. Hibernate se configura con `ddl-auto=validate`.
2. **Testcontainers** para tests de integración — se levanta un contenedor PostgreSQL real (sin H2), conectado automáticamente via `@ServiceConnection`. Flyway se ejecuta como parte del ciclo de vida del test.

## Consecuencias

- **Positivas**:
  - Migraciones versionadas, inmutables y auditables
  - Tests que reflejan el comportamiento real en producción
  - Sin falsos positivos por diferencias entre H2 y PostgreSQL
  - Configuración mínima con `@ServiceConnection`
- **Negativas**:
  - Dependencia de Docker para tests de integración
  - Tiempo de arranque ligeramente mayor en tests (pull de imagen + migraciones)
  - Las migraciones recién creadas requieren pasar por Flyway — no hay generación automática desde entidades

## Alternativas consideradas

| Alternativa | Rechazada por |
|---|---|
| `ddl-auto=update` | Pérdida de control del esquema, migraciones no versionadas |
| H2 en tests | Falsos positivos por diferencias de dialecto SQL |
| Liquibase | Mayor complejidad para el mismo resultado; equipo más familiarizado con Flyway |
