# Documentación del proyecto

Índice de toda la documentación técnica de Oncall.

## Guías de arquitectura y diseño

| Documento | Descripción |
|---|---|
| [architecture.md](architecture.md) | Arquitectura en capas, reglas de dependencia, diagramas |
| [api-guidelines.md](api-guidelines.md) | Diseño REST, formatos request/response, paginación |
| [database.md](database.md) | Convenciones de BD, Flyway, nomenclatura de esquema |
| [telegram.md](telegram.md) | Integración con Telegram: configuración, vinculación, comandos, notificaciones |
| [zadarma.md](zadarma.md) | Integración con Zadarma: redirección VoIP automática por turnos |

## Instrucciones para Copilot

| Documento | Descripción |
|---|---|
| [AGENTS.md](../AGENTS.md) | Personas, workflows y guardrails del agente Copilot |
| [.github/copilot-instructions.md](../.github/copilot-instructions.md) | Instrucciones principales (stack, convenciones, comandos) |
| [.github/instructions/architecture.instructions.md](../.github/instructions/architecture.instructions.md) | Reglas de arquitectura (on-demand) |
| [.github/instructions/api.instructions.md](../.github/instructions/api.instructions.md) | Diseño de API REST (on-demand) |
| [.github/instructions/database.instructions.md](../.github/instructions/database.instructions.md) | Convenciones de base de datos (on-demand) |
| [.github/instructions/adr.instructions.md](../.github/instructions/adr.instructions.md) | Registro de decisiones de arquitectura (on-demand) |

## Decisiones de arquitectura (ADR)

| Documento | Descripción |
|---|---|
| [adr/001-usar-flyway-testcontainers.md](adr/001-usar-flyway-testcontainers.md) | Flyway para migraciones + Testcontainers para tests de integración |
| [adr/002-gestion-turnos-shifts.md](adr/002-gestion-turnos-shifts.md) | Diseño de la gestión de turnos: CRUD, solapamientos, división automática y ajuste adyacente |

## Estructura del proyecto

```
oncall/
├── .github/
│   ├── copilot-instructions.md         → Instrucciones principales para Copilot
│   └── instructions/                   → Instrucciones específicas (API, BD, arquitectura)
├── TASKS.md               → Backlog y plan de trabajo
├── docs/                  → Documentación técnica
│   ├── api-guidelines.md
│   ├── architecture.md
│   ├── database.md
│   └── adr/               → Architecture Decision Records
├── src/main/java/         → Código fuente
├── src/test/java/         → Tests
└── src/main/resources/    → Configuración y migraciones
```
