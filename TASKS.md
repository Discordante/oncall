# Tareas — Oncall

## Sprint actual (Facturación y Liquidación)

- [x] Implementar endpoint de cálculo de nómina/facturación mensual:
  - Nuevo `BillingController` en `GET /api/v1/billing?groupId=&personId=&from=&to=`.
  - Calcula horas totales por persona en un rango de fechas (por defecto mes actual).
  - Busca el precio histórico vigente para cada turno (`on_call_group_price_history`).
  - Prorratea el valor económico bruto: `(horas / 24) × precio_del_día`.
  - Si el precio cambió durante el período, cada turno se calcula con su precio vigente.
- [ ] Endpoint para exportar el resumen de pagos en base a un rango de fechas de un grupo de guardia (facturación mensual).

## Backlog

- [ ] Autenticación/autorización con roles.
- [ ] Notificaciones (email/Slack) inmediatas al realizar un intercambio de horas.
- [ ] Vista de calendario interactiva para ver los rangos de tiempo de cada persona.
- [ ] Exportación de calendarios en formato iCal.

## Sprint: Integración Telegram

- [x] Migración Flyway V3: columnas Telegram en `people` y `on_call_groups`, tabla `telegram_pending_links`.
- [x] Entidades actualizadas: `Person` (+telegramChatId, telegramLinkCode, telegramLinkedAt), `OnCallGroup` (+telegramBotToken, telegramGroupChatId, telegramBotEnabled).
- [x] Nuevo DTO: `OnCallGroupRequest`/`Response` con campos Telegram (token nunca en response).
- [x] Nuevo DTO: `TelegramLinkRequest`/`TelegramLinkResponse` para vinculación.
- [x] Nuevo `TelegramController` con `POST /api/v1/telegram/link` y `GET /api/v1/telegram/link-status`.
- [x] Cliente API Telegram vía `RestClient` (`TelegramApiClient`) — sin librerías externas.
- [x] Servicio `TelegramBotServiceImpl` con polling `@Scheduled`, vinculación por código único, registro de bots por grupo.
- [x] Inicializador `TelegramBotInitializer` que arranca bots de grupos habilitados.
- [x] Comandos: `/info` (ayuda), `/turnos` (calendario 4 semanas vía `ShiftService`), `/start` (vinculación).
- [x] Eventos Spring: `ShiftCreatedEvent`, `ShiftUpdatedEvent`, `ShiftDeletedEvent`.
- [x] Notificaciones automáticas en grupo y a persona vinculada vía `ShiftNotificationListener`.
- [x] Resúmenes programados: diario (8 AM) y semanal (lunes 9 AM) vía `TelegramScheduledNotifier`.
- [x] Migración V4: columna `telegram_group_invite_link` en `on_call_groups`.
- [x] Al configurar bot + chat ID en un grupo, se genera automáticamente un enlace de invitación (`createChatInviteLink`) y se guarda en BD.
- [x] El enlace de invitación se expone en `GET /api/v1/on-call-groups/{id}` como `telegramGroupInviteLink`.
- [x] Tests unitarios: `TelegramBotServiceImplTest` (24 tests), `CommandRouterTest` (9 tests), `InfoCommandTest` (3 tests), `ScheduleCommandTest` (4 tests).
- [x] Tests de integración: `TelegramControllerIntegrationTest` (7 tests) con Testcontainers.
- [x] Total: **122 tests, 0 fallos**.

## Completado

### Sprint: Facturación y Liquidación

- [x] Endpoint `GET /api/v1/billing?groupId=...&personId=...&from=...&to=`.
- [x] Migración Flyway V2: tabla `on_call_group_price_history` con rangos de vigencia (`effective_from`/`effective_to`).
- [x] Entidad `OnCallGroupPriceHistory`, repositorio con query `findEffectivePrice(groupId, date)`.
- [x] DTOs: `BillingPersonDetail` (horas, días, importe por persona) y `BillingReportResponse` (reporte completo).
- [x] Servicio `BillingService` con lógica de cálculo usando `BigDecimal` y precios históricos.
- [x] Tests unitarios (8 tests): cálculo correcto, múltiples personas, precio histórico, filtro por persona, fechas por defecto, grupo inexistente, turno fraccionado.
- [x] Tests de integración (5 tests): dos personas, filtro por persona, período vacío, 404 grupo inexistente, 400 sin groupId.
- [x] Handler global para `MissingServletRequestParameterException` (400 BAD_REQUEST).

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