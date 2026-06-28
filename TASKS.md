# Tareas — Oncall

## Sprint actual (Facturación y Liquidación)

- [ ] Implementar endpoint de cálculo de nómina/facturación mensual:
  - Calcular el sumatorio de la duración de todos los `Shifts` de una persona en un mes.
  - Prorratear el valor económico bruto acumulado según el `gross_price_per_day` del grupo correspondiente.
- [ ] Endpoint para exportar el resumen de pagos mensual de un grupo de guardia.

## Backlog

- [ ] Autenticación/autorización con roles.
- [ ] Notificaciones (email/Slack) inmediatas al realizar un intercambio de horas.
- [ ] Vista de calendario interactiva para ver los rangos de tiempo de cada persona.
- [ ] Exportación de calendarios en formato iCal.

## Completado

### Sprint: Operativa de Turnos e Intercambios

- [x] CRUD completo de Shifts (`/api/v1/shifts`): Repository, Service, DTOs, Mapper, Controller.
- [x] Listado con filtros: `GET /api/v1/shifts?personId=&onCallGroupId=&from=&to=`.
- [x] Validación de solapamientos (misma persona): `create()` lanza 409 si la persona ya tiene un turno en el rango.
- [x] División automática (distinta persona): al crear un turno que solapa con otra persona, se divide el turno existente (tramos delantero y trasero).
- [x] Ajuste de turnos adyacentes en update: al modificar un turno, los turnos anterior/siguiente de la misma persona se ajustan automáticamente.
- [x] Tests unitarios (18 tests): CRUD, filtros, solapamientos, división (5 variantes), ajuste adyacente.
- [x] Tests de integración (5 tests): flujo completo REST con Testcontainers, solapamiento 409, división automática, filtros.

### Sprint: Core, Modelado y Catálogos

- [x] Definir el modelo de datos final con rangos de tiempo (`OffsetDateTime`):
  - `Application`, `Person`, `OnCallGroup`, `Shift`.
- [x] Crear migración Flyway V1 con la estructura de tablas y relaciones necesarias.
- [x] Implementar entidades JPA (controlando que `Shift` use rangos de tiempo y validación de que `start_time` < `end_time`).
- [x] Implementar CRUDs base + excepción global + response wrapper:
  - `Applications` y `People` (catálogos).
  - `OnCallGroups` (gestión de configuraciones de guardias con ManyToMany).
- [x] Implementar infraestructura transversal (ApiResponse, ValidationError, GlobalExceptionHandler).
- [x] Tests unitarios de servicios (22 tests).
- [x] Tests de integración con Testcontainers (9 tests).

### Setup del proyecto

- [x] Inicializar proyecto con Spring Boot 4.1.0 + Java 25.
- [x] Configurar Flyway + PostgreSQL + JPA.
- [x] Configurar Testcontainers para tests de integración.
- [x] Crear estructura de paquetes (capas tradicional).
- [x] Documentar arquitectura, BD y API guidelines.
- [x] Crear ADR inicial.