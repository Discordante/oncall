# Integración Telegram

## Descripción general

Oncall permite asociar opcionalmente un bot de Telegram a cada grupo de guardia.
El bot puede enviar notificaciones automáticas, responder comandos en español
(`/info`, `/turnos`) y gestionar la vinculación de usuarios mediante códigos
únicos de verificación.

La integración se implementa **sin librerías externas**: usa directamente la
API HTTP de Telegram (`api.telegram.org/bot<token>/...`) a través de
`RestClient` de Spring Boot, lo que evita problemas de compatibilidad con
Java 25.

---

## Arquitectura

```
┌──────────────────────────────────────────────────────────────┐
│                    Capas del proyecto                         │
├──────────────────────────────────────────────────────────────┤
│  telegram/       ← Nuevo paquete para toda la integración     │
│  controller/     ← TelegramController (REST de vinculación)   │
│  dto/telegram/   ← TelegramLinkRequest / Response             │
│  service/        ← OnCallGroupServiceImpl (hook invite link)  │
│  entity/         ← Person, OnCallGroup (+ campos Telegram)    │
│  repository/     ← PersonRepository, OnCallGroupRepository    │
└──────────────────────────────────────────────────────────────┘
```

### Paquete `telegram/` (14 archivos)

| Subpaquete | Archivo | Propósito |
|---|---|---|
| `command/` | `TelegramCommand.java` | Interfaz funcional para comandos |
| `command/` | `InfoCommand.java` | `/info` — lista de comandos |
| `command/` | `ScheduleCommand.java` | `/turnos` — calendario 4 semanas |
| `bot/` | `CommandRouter.java` | Router de comandos entrantes |
| `client/` | `TelegramApiClient.java` | Cliente HTTP para API de Telegram |
| `config/` | `TelegramBotConfig.java` | Config polling, webhook |
| `config/` | `TelegramBotInitializer.java` | Arranca bots al iniciar app |
| `event/` | `ShiftCreatedEvent.java` | Evento creación de turno |
| `event/` | `ShiftUpdatedEvent.java` | Evento actualización de turno |
| `event/` | `ShiftDeletedEvent.java` | Evento borrado de turno |
| `service/` | `TelegramBotService.java` | Interfaz del servicio Telegram |
| `service/` | `TelegramBotServiceImpl.java` | Implementación (polling, linking, notis) |
| `service/` | `TelegramScheduledNotifier.java` | Resúmenes diarios y semanales |
| `service/` | `ShiftNotificationListener.java` | Escucha eventos de turnos |

### Archivos relacionados fuera de `telegram/`

| Archivo | Cambio |
|---|---|
| `controller/TelegramController.java` | `POST /api/v1/telegram/link`, `GET /api/v1/telegram/link-status` |
| `dto/telegram/TelegramLinkRequest.java` | `{ code }` |
| `dto/telegram/TelegramLinkResponse.java` | `{ success, message }` |
| `dto/OnCallGroupRequest.java` | + `telegramBotToken`, `telegramGroupChatId`, `telegramBotEnabled` |
| `dto/OnCallGroupResponse.java` | + `telegramGroupChatId`, `telegramBotEnabled`, `telegramGroupInviteLink` |
| `entity/Person.java` | + `telegramChatId`, `telegramLinkCode`, `telegramLinkedAt` |
| `entity/OnCallGroup.java` | + `telegramBotToken`, `telegramGroupChatId`, `telegramBotEnabled`, `telegramGroupInviteLink` |
| `entity/TelegramPendingLink.java` | Entidad para códigos de verificación temporales |
| `repository/PersonRepository.java` | + `findByTelegramChatId`, `findByTelegramLinkCode` |
| `repository/OnCallGroupRepository.java` | + `findByTelegramBotEnabledTrue` |
| `repository/TelegramPendingLinkRepository.java` | Repositorio para `telegram_pending_links` |
| `service/OnCallGroupServiceImpl.java` | Registra bot y genera invite link al crear/actualizar |
| `service/ShiftServiceImpl.java` | Publica eventos Spring (`ApplicationEventPublisher`) |
| `mapper/PersonMapper.java` | + `telegramLinked` en response |
| `mapper/OnCallGroupMapper.java` | + campos Telegram (token nunca en response) |
| `telegram/config/TelegramBotConfig.java` | `@EnableScheduling`, propiedades `telegram.bot.*` |

---

## Base de datos

### Migraciones

| Migración | Archivo | Cambios |
|---|---|---|
| V3 | `V3__add_telegram_integration.sql` | Columnas Telegram en `people` y `on_call_groups`, tabla `telegram_pending_links` |
| V4 | `V4__add_telegram_invite_link.sql` | Columna `telegram_group_invite_link` en `on_call_groups` |
| V9 | `V9__add_telegram_user_id.sql` | Vinculación por `telegram_user_id` (no chat id) en `people` y `telegram_pending_links` |

### Columnas añadidas

**Tabla `people`**

| Columna | Tipo | Descripción |
|---|---|---|
| `telegram_user_id` | `VARCHAR(255) UNIQUE` | ID de usuario de Telegram (`message.from.id`), identidad única de la cuenta vinculada |
| `telegram_chat_id` | `VARCHAR(255) UNIQUE` | ID del chat privado usado para DMs (solo se guarda si `/start` se envió desde chat privado) |
| `telegram_link_code` | `VARCHAR(36) UNIQUE` | Código único de verificación (UUID sin guiones) |
| `telegram_linked_at` | `TIMESTAMP WITH TIME ZONE` | Cuándo se vinculó la cuenta |

**Tabla `on_call_groups`**

| Columna | Tipo | Descripción |
|---|---|---|
| `telegram_bot_token` | `VARCHAR(255)` | Token del bot de Telegram (de BotFather) |
| `telegram_group_chat_id` | `VARCHAR(255)` | ID del chat/grupo de Telegram donde opera el bot |
| `telegram_bot_enabled` | `BOOLEAN NOT NULL DEFAULT FALSE` | Activa/desactiva la integración |
| `telegram_group_invite_link` | `VARCHAR(255)` | Enlace de invitación generado por `createChatInviteLink` |

**Tabla `telegram_pending_links`**

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | `UUID PK` | ID único |
| `user_id` | `VARCHAR(255) NOT NULL` | ID de usuario de Telegram que solicitó el código |
| `chat_id` | `VARCHAR(255) NOT NULL` | Chat donde se envió `/start` (privado o grupo) |
| `chat_type` | `VARCHAR(32)` | Tipo de chat: `private`, `group` o `supergroup` |
| `link_code` | `VARCHAR(36) NOT NULL UNIQUE` | Código de verificación |
| `created_at` | `TIMESTAMP WITH TIME ZONE NOT NULL` | Fecha de creación |
| `expires_at` | `TIMESTAMP WITH TIME ZONE NOT NULL` | Fecha de expiración (15 min) |

---

## Flujo de configuración de un grupo

### 1. Requisitos previos

1. El admin crea un bot con **BotFather** en Telegram y obtiene el token
2. El admin **crea un grupo en Telegram**, añade el bot como administrador
   y obtiene el **chat ID** del grupo (se puede ver en apps como
   "Get Chat ID" o viendo las actualizaciones del bot)

### 2. Configuración en la API REST

```
POST /api/v1/on-call-groups
Content-Type: application/json

{
  "name": "Guardia-Sistemas-Q1",
  "telegramBotToken": "123456:ABC-DEF1234...",
  "telegramGroupChatId": "-1001234567890",
  "telegramBotEnabled": true,
  "personIds": ["uuid-1", "uuid-2"],
  "applicationIds": []
}
```

### 3. Lo que ocurre automáticamente

```
1. Se crea/actualiza el grupo de guardia en BD
2. Se registra el bot en el sistema (inicia polling)
3. Se verifica que el bot está en el grupo (sendChatAction)
4. Se genera un enlace de invitación (createChatInviteLink)
5. El enlace se guarda en telegram_group_invite_link
6. El enlace se expone en la respuesta de la API
```

### 4. Compartir el enlace

```
GET /api/v1/on-call-groups/{id}

Response:
{
  "data": {
    "id": "...",
    "name": "Guardia-Sistemas-Q1",
    "telegramGroupChatId": "-1001234567890",
    "telegramBotEnabled": true,
    "telegramGroupInviteLink": "https://t.me/+AbCdEf1234",
    ...
  }
}
```

El admin puede compartir este enlace manualmente con los miembros del grupo
(por email, Slack, etc.). Los miembros se unen al grupo de Telegram mediante
el enlace, sin necesidad de haber interactuado antes con el bot.

---

## Flujo de vinculación de usuario

Una vez que un usuario está en el grupo de Telegram, puede vincular su cuenta
de Oncall con su chat de Telegram para recibir notificaciones personalizadas
y usar los comandos del bot.

```
1. Usuario escribe /start (en el chat privado con el bot o en el grupo de guardia)
        │
2. Bot genera un código UUID único y lo guarda en
   telegram_pending_links (expira en 15 min)
        │
3. Bot responde:
   "Tu código de verificación: a1b2c3d4...
    Ve a la app web e ingresa el código."
        │
4. Usuario va a la app web e ingresa el código
   POST /api/v1/telegram/link { "code": "a1b2c3d4..." }
        │
5. Sistema busca el código en telegram_pending_links,
   obtiene el user_id de Telegram y lo asigna al Person autenticado.
   El chat_id solo se guarda si el /start se envió desde un chat privado
   (es el destino de los mensajes directos)
        │
6. Bot confirma: "✅ Cuenta vinculada correctamente."
        │
7. A partir de ahora el usuario recibe:
   ├── Notificaciones de cambios en sus turnos
   ├── Resúmenes diarios/semanales
   └── Puede usar /info, /turnos en el grupo
```

> **Identidad por user_id, no por chat_id.** En un grupo de Telegram todos
> los miembros comparten el mismo `chat.id`; la identidad única de cada
> cuenta es `message.from.id`. La vinculación usa ese `user_id`, por lo que
> `/start` desde el grupo identifica correctamente a cada persona. El
> `chat_id` privado solo se almacena como destino de mensajes directos y,
> si el usuario solo interactuó en el grupo, queda a null (las
> notificaciones personales no pueden enviarse por DM hasta que el usuario
> escriba también al bot en privado).

### API de vinculación

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/v1/telegram/link` | Vincular cuenta (body: `{ "code": "..." }`) |
| `GET` | `/api/v1/telegram/link-status?code=...` | Consultar estado de un código |

---

## Comandos del bot

Actualmente disponibles:

| Comando | Descripción |
|---|---|
| `/start` | Inicia el proceso de vinculación (genera código) |
| `/info` | Muestra la lista de comandos disponibles |
| `/turnos` | Muestra el calendario de guardias para las próximas 4 semanas |

Los comandos se procesan mediante **long polling**. El bot sondea
periódicamente la API de Telegram (`getUpdates`) y el `CommandRouter`
dirige cada comando al handler correspondiente.

### Verificación de vinculación

Si un usuario no vinculado intenta usar `/info` o `/turnos`, el bot
responde pidiéndole que primero se vincule con `/start`.

---

## Notificaciones automáticas

### Por eventos (cambios en turnos)

Cuando se crea, modifica o elimina un turno, `ShiftServiceImpl` publica
un evento Spring que `ShiftNotificationListener` recibe y envía
notificaciones a:

1. **El grupo de Telegram** del grupo de guardia afectado
2. **La persona vinculada** involucrada en el cambio (si tiene Telegram
   vinculado)

| Evento | Mensaje en grupo | Mensaje a persona |
|---|---|---|
| `ShiftCreatedEvent` | 🆕 Nueva guardia asignada | 🆕 Se te ha asignado una nueva guardia |
| `ShiftUpdatedEvent` | 🔄 Guardia modificada | 🔄 Tu guardia ha sido modificada |
| `ShiftDeletedEvent` | ❌ Guardia cancelada | ❌ Tu guardia ha sido cancelada |

### Por schedule (resúmenes)

El `TelegramScheduledNotifier` envía resúmenes programados a todos los
grupos que tengan el bot habilitado:

| Resumen | Horario | Contenido |
|---|---|---|
| Diario | 8:00 AM | Guardias del día actual |
| Semanal | Lunes 9:00 AM | Guardias de la semana |

---

## Arquitectura del bot

### Recepción de mensajes (Polling)

```java
@Scheduled(fixedDelayString = "${telegram.bot.polling-interval-ms:5000}")
```

El bot usa **long polling** en lugar de webhooks (configurable). En cada
ciclo, para cada grupo con bot activo, llama a `getUpdates()` y procesa
los mensajes recibidos.

**Configuración** (en `application.yml`):

```yaml
telegram:
  bot:
    polling-enabled: true
    polling-interval-ms: 5000
    webhook-base-url: ${TELEGRAM_WEBHOOK_URL:}
```

### Registro de bots

El `TelegramBotInitializer` (implementa `ApplicationRunner`) se ejecuta al
iniciar la aplicación y registra todos los bots de grupos con
`telegramBotEnabled = true`.

Cuando se crea o actualiza un grupo con Telegram habilitado,
`OnCallGroupServiceImpl` llama a:
1. `registerGroupBot()` — inicia el polling para ese grupo
2. `generateGroupInviteLink()` — genera el enlace de invitación

### Cliente API

`TelegramApiClient` usa `RestClient` de Spring para llamar a:

| Método | API de Telegram |
|---|---|
| `sendMessage(botToken, chatId, text)` | `sendMessage` |
| `setWebhook(botToken, url)` | `setWebhook` |
| `deleteWebhook(botToken)` | `deleteWebhook` |
| `getUpdates(botToken, offset, timeout)` | `getUpdates` |
| `createChatInviteLink(botToken, chatId)` | `createChatInviteLink` |
| `verifyBotInGroup(botToken, chatId)` | `sendChatAction` |

---

## Seguridad

- El `telegramBotToken` se almacena en BD pero **nunca se expone**
  en las respuestas de la API REST
- El `OnCallGroupResponse` solo incluye `telegramGroupChatId`,
  `telegramBotEnabled` y `telegramGroupInviteLink`
- Los códigos de verificación expiran a los 15 minutos
- Al vincular una cuenta, si otro usuario ya tenía ese `chatId`,
  se desvincula automáticamente del anterior

---

## Limitaciones conocidas

| Limitación | Detalle |
|---|---|
| `createNewGroup` | La API de Telegram exige al menos 1 usuario que haya interactuado con el bot. No se puede crear el grupo automáticamente. El admin debe crearlo manualmente. |
| `addChatMember` | Solo funciona si el usuario ya ha iniciado conversación con el bot. Por eso usamos invite links para que los miembros se unan. |
| Inicio de conversación | Los usuarios deben hacer `/start` manualmente para vincular su cuenta. No se puede enviar mensajes a usuarios que no hayan iniciado conversación. |
| Bot admin | El bot debe ser administrador del grupo para poder generar `createChatInviteLink` y enviar mensajes. |

---

## Próximas mejoras posibles

- [ ] Envío de email con el invite link a los miembros del grupo
- [ ] Comando `/mis-turnos` para ver turnos del usuario
- [ ] Comando `/swap` para solicitar intercambio de turnos
- [ ] Soporte multi-idioma
- [ ] Tests unitarios e integración de componentes Telegram
- [ ] Dashboard web de administración de bots
