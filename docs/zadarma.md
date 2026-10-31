# Integración Zadarma — Redirección de llamadas VoIP

## Descripción general

Oncall se integra con la API de Zadarma para redirigir automáticamente las llamadas entrantes al teléfono de la persona que está de guardia **en ese momento**. La redirección se activa cuando comienza un turno y se actualiza automáticamente cuando termina, saltando al siguiente turno activo.

La integración usa directamente la API HTTP de Zadarma (`api.zadarma.com/v1/`) a través de `RestClient` de Spring Boot.

**Solo se necesita 1 llamada a la API por redirección** (`PUT /v1/sip/redirection/`), no el antiguo flujo de 3 llamadas que usaba el script `oncall.sh`.

---

## Arquitectura de componentes

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Capas del proyecto                           │
├─────────────────────────────────────────────────────────────────────┤
│  zadarma/              ← Paquete exclusivo de la integración VoIP    │
│  ├── client/           ← Clientes HTTP con autenticación HMAC-SHA1   │
│  │   ├── ZadarmaApiClient        (escritura: PUT redirección)        │
│  │   ├── ZadarmaReadonlyClient   (lectura: GET listas, estadísticas) │
│  │   └── ZadarmaResponse         (record genérico de respuesta)      │
│  ├── service/          ← Lógica de negocio                           │
│  │   ├── ZadarmaService          (interfaz pública)                  │
│  │   └── ZadarmaServiceImpl      (implementación)                    │
│  ├── scheduler/        ← Núcleo de la automatización temporal        │
│  │   └── CallRedirectionScheduler                                    │
│  ├── dto/              ← DTOs para respuestas de la API Zadarma       │
│  └── event/            ← Eventos y listeners para alertas Telegram   │
│      ├── ZadarmaRedirectSuccessEvent                                 │
│      ├── ZadarmaRedirectFailedEvent                                  │
│      └── ZadarmaRedirectAlertListener                                │
│                                                                      │
│  entity/               ← Modelos de datos                            │
│  ├── OnCallGroup       (→ zadarmaConfig: ZadarmaGroupConfig FK)      │
│  ├── ZadarmaGroupConfig (tabla zadarma_configs, nueva en V8)         │
│  └── Shift             (campos redirect_status, intentos, etc.)      │
│                                                                      │
│  config/ZadarmaConfig  ← @ConfigurationProperties zadarma.api.*     │
│  controller/ZadarmaController  ← Endpoints GET de consulta          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Modelo de datos

### Tabla `zadarma_configs` (migración V8)

La configuración de Zadarma está **separada de `on_call_groups`** en su propia tabla. Un grupo puede no tener configuración (relación 1:1 opcional).

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | `UUID PK` | Identificador único |
| `sip_id` | `VARCHAR(255)` | ID del SIP en Zadarma (ej. `"921495"`) |
| `redirection_type` | `VARCHAR(50) DEFAULT 'sip'` | Tipo de redirección: `sip` o `pbx` |
| `active` | `BOOLEAN NOT NULL DEFAULT TRUE` | Activa (`true`) o desactiva (`false`) la redirección |
| `created_at` | `TIMESTAMPTZ` | Fecha de creación |
| `updated_at` | `TIMESTAMPTZ` | Fecha de última modificación |

### Relación con `on_call_groups`

```
on_call_groups
  └── zadarma_config_id (UUID, UNIQUE, nullable FK → zadarma_configs.id)
```

- Si `zadarma_config_id IS NULL` → el grupo no usa Zadarma (backward compatible).
- Si `zadarma_config_id IS NOT NULL` → el grupo tiene config Zadarma; la redirección se ejecuta si `active = true`.

### Tabla `shifts` (campos de estado de redirección, migración V6)

| Columna | Tipo | Descripción |
|---|---|---|
| `redirect_status` | `VARCHAR(20)` | `null` (pendiente), `ok`, `failed` |
| `redirect_failed_at` | `TIMESTAMPTZ` | Cuándo falló la redirección por última vez |
| `redirect_last_attempt_at` | `TIMESTAMPTZ` | Cuándo se intentó la redirección por última vez |
| `redirect_retry_count` | `INT DEFAULT 0` | Número de reintentos acumulados |

---

## API REST

### Configurar Zadarma en un grupo

Al crear o actualizar un `OnCallGroup`, el cuerpo de la petición acepta tres campos opcionales de Zadarma:

```json
POST /api/v1/on-call-groups
{
  "name": "Crew OnCall",
  "grossPricePerDay": "150.00",
  "zadarmaSipId": "921495",
  "zadarmaRedirectionType": "sip",
  "zadarmaActive": true
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `zadarmaSipId` | `String` (opcional) | ID del SIP en Zadarma. Si es `null`, se elimina la config completa. |
| `zadarmaRedirectionType` | `String` (opcional) | `"sip"` (por defecto) o `"pbx"` |
| `zadarmaActive` | `Boolean` (opcional) | `true` (por defecto) activa la redirección; `false` la desactiva sin eliminar la config |

La respuesta incluye los tres campos reflejando el estado guardado (`zadarmaActive` es `null` cuando el grupo no tiene config Zadarma).

### Activar y desactivar la redirección

Para **desactivar temporalmente** la redirección sin perder la configuración del SIP:

```json
PUT /api/v1/on-call-groups/{id}
{
  "name": "Crew OnCall",
  "zadarmaSipId": "921495",
  "zadarmaActive": false
}
```

Efecto inmediato: la próxima vez que el scheduler ejecute `redirectToOnCallPerson` para un turno activo de este grupo, llamará a `PUT /v1/sip/redirection/` con `status=off`, desactivando la redirección en Zadarma.

Para **reactivar**:

```json
PUT /api/v1/on-call-groups/{id}
{
  "name": "Crew OnCall",
  "zadarmaSipId": "921495",
  "zadarmaActive": true
}
```

El fallback checker (cada 5 min) detectará el cambio y redirigirá al turno activo actual.

Para **eliminar completamente** la config de Zadarma:

```json
PUT /api/v1/on-call-groups/{id}
{
  "name": "Crew OnCall",
  "zadarmaSipId": null
}
```

Esto borra la fila en `zadarma_configs` (via `orphanRemoval=true`).

### Endpoints de consulta (solo lectura)

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/v1/zadarma/sips` | Lista todos los SIPs de la cuenta Zadarma, enriquecidos con el nombre del grupo que los usa |
| `GET` | `/api/v1/zadarma/sip-redirections[?id=...]` | Estado actual de la redirección (destino, condición, etc.) |
| `GET` | `/api/v1/zadarma/calls-summary[?start=&end=&sip=]` | Estadísticas de llamadas entrantes, ordenadas de más reciente a más antigua |

### Clasificación de llamadas de comprobación

`calls-summary` devuelve en cada registro el campo `checkCall`. Vale `true` cuando el número de **origen** de la llamada pertenece a una persona dada de alta en la aplicación (`people.phone_number`), es decir, se trata de una llamada de comprobación y no de una llamada real de un cliente. La comparación normaliza ambos números a sus 9 dígitos significativos, por lo que `+34 600 000 001`, `34600000001` y `600000001` se consideran el mismo número.

La pestaña de llamadas del panel muestra estas llamadas con el estado **Comprobación** y no las contabiliza como *Atendidas* ni *No atendidas*.

---

## Creación automática de incidencias

Al sincronizar llamadas desde Zadarma (`CallSyncServiceImpl`), se crea una incidencia en borrador **solo** cuando:

1. La llamada tiene `disposition = answered` (atendida), y
2. La llamada **no** es de comprobación (`checkCall = false`), es decir, su origen no es el teléfono de una persona de alta, y
3. El SIP de la llamada pertenece a un grupo de guardia.

La incidencia se crea sin aplicación asignada (el usuario la asigna al editarla) y la llamada no se vincula automáticamente. La incidencia queda asignada al grupo de guardia de la llamada.

### Aislamiento por grupo

Cada incidencia pertenece a un grupo de guardia (`incidents.on_call_group_id`, migración V15). El endpoint `/api/v1/incidents` acepta los filtros `onCallGroupId`, `applicationId` y el rango de fecha de inicio `startedFrom`/`startedTo` (ISO 8601, ambos inclusive; si la incidencia no tiene `started_at` se usa `created_at`). El panel siempre envía el grupo activo, por lo que cada grupo solo ve sus propias incidencias (también en el modal de asociar llamada a incidencia), y por defecto filtra por el mes anterior y el mes en curso. Al crear una incidencia desde el panel, el `onCallGroupId` se envía en el cuerpo; si no se envía, el servicio lo infiere del grupo de las llamadas vinculadas.

---

## Flujo completo de redirección

### Condiciones previas necesarias

Para que la redirección funcione, se deben cumplir **todas** estas condiciones:

1. El grupo tiene una `ZadarmaGroupConfig` con `sipId` no vacío.
2. La config tiene `active = true`.
3. El turno tiene una `Person` asignada con `phoneNumber` configurado.
4. Las variables de entorno `ZADARMA_API_KEY` y `ZADARMA_API_SECRET` están definidas.

Si **cualquiera** de las condiciones 1–2 falla → la redirección se omite silenciosamente (no es un error).  
Si la condición 3 falla → la redirección falla y se alerta.

---

### Flujo 1: Creación de un turno futuro

```
POST /api/v1/shifts { groupId, personId, startTime: T+7d, endTime: T+14d }
  │
  └─► ShiftServiceImpl.create()
        └─► Shift guardado en BD (redirectStatus = null)
        └─► CallRedirectionScheduler.scheduleShiftTransitions(shift)
              ├── Programa tarea A: en T+7d → redirectToOnCallPerson(shiftId)
              └── Programa tarea B: en T+14d → handleShiftEnd(shiftId)
                                              (log de fin de turno)

[En este punto NO se llama a la API de Zadarma]
```

### Flujo 2: Llegada del instante de inicio del turno

```
TaskScheduler dispara redirectToOnCallPerson(shiftId)
  │
  ├── El turno ya ha terminado → skip
  ├── El grupo no tiene ZadarmaGroupConfig o sipId → skip (silencioso)
  ├── La config tiene active=false → disableSipRedirection() → return
  │
  ├── GET /v1/sip/redirection/?id=921495
  │    └── ¿El destino actual ya es el teléfono esperado?
  │          └── SÍ → skip (idempotencia, evita PUT redundante)
  │
  └── PUT /v1/sip/redirection/ { id: "921495", type: "phone", number: "34653177761" }
        ├── Éxito → redirectStatus = "ok", resetRedirectRetryCount = 0
        │           publica ZadarmaRedirectSuccessEvent → notificación Telegram
        └── Fallo → redirectStatus = "failed"
                    redirectRetryCount++
                    publica ZadarmaRedirectFailedEvent → alerta Telegram
                    programa reintento en 30s×(retryCount+1)
```

### Flujo 3: Fin del turno

```
TaskScheduler dispara handleShiftEnd(shiftId)
  │
  └── Solo registra log del fin de turno.
      El siguiente turno — si existe — ya tiene programada su propia
      tarea de inicio que se encargará de actualizar la redirección.
      Si no hay siguiente turno, la redirección en Zadarma queda apuntando
      al último destino (el número del operador saliente).
```

> **Nota de diseño**: No se desactiva la redirección al final del turno. Esto garantiza que las llamadas siempre tengan destino (aunque sea el operador saliente) mientras no hay turno activo, evitando llamadas no atendidas en brechas entre turnos.

### Flujo 4: Actualización de un turno existente

```
PUT /api/v1/shifts/{id} { startTime: nueva_hora, ... }
  │
  └─► ShiftServiceImpl.update()
        ├── CallRedirectionScheduler.cancelShiftTransitions(shiftId)
        │     └── Cancela las tareas A y B del turno anterior
        └── CallRedirectionScheduler.scheduleShiftTransitions(shift)
              └── Reprograma con los nuevos instantes
```

### Flujo 5: Eliminación de un turno activo

```
DELETE /api/v1/shifts/{id}
  │
  └─► ShiftServiceImpl.delete()
        ├── CallRedirectionScheduler.cancelShiftTransitions(shiftId)
        └── Si el turno estaba activo:
              CallRedirectionScheduler.handleActiveShiftRemoved(groupId)
                ├── Busca el siguiente turno activo del grupo
                ├── Si existe → redirectToOnCallPerson(siguienteTurnoId)
                └── Si no existe → log "sin turno activo, manteniendo última redirección"
```

### Flujo 6: Reinicio del servidor

```
ApplicationReadyEvent
  │
  └─► CallRedirectionScheduler.onStartup()
        └─► Consulta BD: todos los turnos con endTime > NOW()
              │
              ├── Para turnos YA activos (startTime ≤ NOW < endTime):
              │     └── scheduleShiftTransitions → tarea de inicio ejecutada en 2s
              │
              └── Para turnos futuros (startTime > NOW):
                    └── scheduleShiftTransitions → tarea programada para el futuro
```

El reinicio es completamente transparente: en ~2 segundos la redirección es correcta para cualquier turno activo.

---

## Fallback checker periódico

```
@Scheduled(fixedDelayString = "${zadarma.redirect.fallback-check-interval-seconds:300}000")
// Por defecto: cada 300 segundos (5 minutos)
```

El fallback checker garantiza la **consistencia entre el estado local y el estado en la API de Zadarma** ante cualquier desviación (cambios manuales en el panel Zadarma, fallos de red transitorios, etc.).

```
Cada 5 minutos:
  │
  └─► Consulta BD: todos los turnos activos ahora (startTime ≤ NOW < endTime)
        │
        └─► Para cada turno activo:
              └─► redirectToOnCallPerson(shiftId)
                    ├── Si active=false → desactiva redirección
                    ├── Si destino ya es correcto → skip (idempotente)
                    └── Si destino es incorrecto → corrige con PUT
```

El checker también recupera automáticamente los turnos con `redirectStatus = "failed"` que hayan agotado los reintentos automáticos.

---

## Lógica del campo `active`

El campo `active` en `zadarma_configs` permite habilitar/deshabilitar la redirección **sin perder la configuración del SIP ID**.

| Estado | Comportamiento |
|---|---|
| Config ausente (`zadarmaConfig = null`) | El grupo no usa Zadarma. Todas las operaciones de redirección son no-ops silenciosos. |
| Config presente, `active = true` | Redirección habitual. El scheduler redirige al turno activo actual. |
| Config presente, `active = false` | El scheduler llama a `disableSipRedirection()` en la API de Zadarma. Las llamadas al SIP dejan de redirigirse. La config (`sipId`, `redirectionType`) se conserva para poder reactivar. |

**¿Cuándo se aplica el cambio de `active`?** El cambio no es instantáneo por diseño. Se aplica en la siguiente ejecución de `redirectToOnCallPerson` para un turno activo, que puede ser:
- Cuando el fallback checker se ejecuta (máximo 5 minutos).
- Cuando empieza el siguiente turno.
- Cuando se actualiza o elimina un turno activo del grupo.

---

## Reintentos automáticos

Cuando una redirección falla (error de API, red, etc.):

```
Intento 1 falla
  └─► redirectStatus = "failed", redirectRetryCount = 1
      └─► Reintento en 30s  (initialDelay × 1)

Intento 2 falla
  └─► redirectStatus = "failed", redirectRetryCount = 2
      └─► Reintento en 60s  (initialDelay × 2)

Intento 3 falla
  └─► redirectStatus = "failed", redirectRetryCount = 3
      └─► Reintento en 90s  (initialDelay × 3)

...

Intento 10 falla (retryMaxAttempts)
  └─► No más reintentos automáticos inmediatos
      └─► El fallback checker (cada 5 min) sigue intentándolo indefinidamente
```

La fórmula del retardo es: `retardo = initialDelaySeconds × retryCount` (crecimiento lineal).

**Durante los reintentos, la redirección anterior en Zadarma permanece activa** — las llamadas no se pierden, van al último destino configurado correctamente.

---

## Alertas de Telegram

El listener `ZadarmaRedirectAlertListener` envía mensajes al chat de Telegram del grupo en dos situaciones:

**Redirección exitosa** (nuevo turno activado):
```
✅ Redirección de llamadas actualizada
📋 Grupo: Crew OnCall
👤 Persona de guardia: Alice García
📞 Teléfono: ***7761
⏰ Turno: 2026-07-10T00:00:00Z → 2026-07-17T08:00:00Z
```

**Fallo de redirección**:
```
⚠️ Fallo en la redirección de llamadas
📋 Grupo: Crew OnCall
👤 Persona de guardia: Alice García
📞 Teléfono: ***7761
❌ Error: Zadarma API returned error or was unreachable
🔄 Reintento: 3/10
🕐 Hora del fallo: 2026-07-10T00:01:30Z
```

---

## Autenticación con la API de Zadarma

Cada petición incluye una cabecera `Authorization` con firma HMAC-SHA1:

```
Authorization: <API_KEY>:<Base64(HMAC-SHA1(secret, ruta+queryString+MD5(queryString)))>
```

Algoritmo exacto:
1. Ordenar parámetros del cuerpo/query alfabéticamente por clave.
2. Construir query string (URL encoding RFC 1738).
3. Calcular `MD5(queryString)`.
4. Data para la firma: `rutaEndpoint + queryString + md5(queryString)`.
5. Firmar con HMAC-SHA1 usando el API secret.
6. Codificar en Base64 la representación hex del HMAC (no los bytes raw).
7. Header final: `Authorization: {apiKey}:{base64Signature}`.

Implementado en `ZadarmaApiClient` y `ZadarmaReadonlyClient`.

---

## Normalización de números de teléfono

Antes de enviar un número a Zadarma se normaliza en `ZadarmaServiceImpl.normalizePhoneNumber()`:

- Se eliminan: `+`, espacios, guiones, paréntesis, puntos
- Se valida que solo contenga dígitos
- Se valida longitud entre 7 y 15 dígitos (estándar E.164)

Ejemplos:
| Entrada | Salida |
|---|---|
| `+34 653 177 761` | `34653177761` |
| `0034 653 177 761` | `0034653177761` |
| `653-177-761` | `653177761` |
| `(34) 653.177.761` | `34653177761` |

Si una persona no tiene `phoneNumber` configurado, o el número no pasa la validación, la redirección falla con error (no es silenciosa).

---

## Configuración

### Variables de entorno

```bash
export ZADARMA_API_KEY=tu_api_key
export ZADARMA_API_SECRET=tu_api_secret
```

### `application.yml`

```yaml
zadarma:
  api:
    key: ${ZADARMA_API_KEY:}
    secret: ${ZADARMA_API_SECRET:}
    base-url: https://api.zadarma.com
  redirect:
    retry-max-attempts: 10               # Máximo de reintentos por fallo
    retry-initial-delay-seconds: 30      # Retardo base del backoff lineal
    fallback-check-interval-seconds: 300 # Frecuencia del checker (5 min)
```

---

## Resumen de todas las fuentes de disparo

| Disparador | Quién lo llama | Cuándo ocurre |
|---|---|---|
| Inicio de turno (puntual) | `TaskScheduler` | Exactamente en `shift.startTime` |
| Turno ya activo al crear | `TaskScheduler` | 2 segundos después de la creación del turno |
| Turno ya activo al arrancar | `TaskScheduler` | 2 segundos después del `ApplicationReadyEvent` |
| Actualización de turno | `ShiftServiceImpl.update()` | Al guardar el PUT, cancela y reprograma |
| Borrado de turno activo | `ShiftServiceImpl.delete()` | Al confirmar el DELETE, busca siguiente turno |
| Fallback periódico | `@Scheduled` | Cada 5 minutos (configurable) |
| Reintento por fallo | `TaskScheduler` (backoff lineal) | 30s, 60s, 90s… hasta 10 intentos |

---

## Árbol de decisión de `redirectToOnCallPerson`

```
redirectToOnCallPerson(shiftId)
  │
  ├── ¿Existe el turno en BD?
  │     NO → return (el turno fue borrado, ok)
  │
  ├── ¿El turno ya ha terminado (endTime ≤ now)?
  │     SÍ → return (llegó tarde, skip)
  │
  ├── ¿El grupo tiene ZadarmaGroupConfig con sipId?
  │     NO → return (grupo sin Zadarma, silencioso)
  │
  ├── ¿active = false?
  │     SÍ → PUT /v1/sip/redirection/ { status: "off" } → return
  │
  ├── GET /v1/sip/redirection/?id={sipId}
  │     → ¿destino actual == normalizar(person.phoneNumber)
  │          AND redirectStatus != "failed"?
  │               SÍ → return (ya está correcto, idempotente)
  │
  └── PUT /v1/sip/redirection/ { id: sipId, type: "phone", number: phoneNormalizado }
        ├── Éxito → redirectStatus = "ok", retryCount = 0
        │           publica ZadarmaRedirectSuccessEvent
        └── Fallo → redirectStatus = "failed", retryCount++
                    publica ZadarmaRedirectFailedEvent
                    programa reintento si retryCount < maxRetries
```

---

## Historial de migraciones relevantes

| Versión | Archivo | Cambios |
|---|---|---|
| V6 | `V6__add_zadarma_integration.sql` | Añade `zadarma_sip_id`, `zadarma_redirection_type` a `on_call_groups`; añade campos `redirect_*` a `shifts` |
| V8 | `V8__extract_zadarma_config.sql` | Extrae los campos Zadarma de `on_call_groups` a la nueva tabla `zadarma_configs`; añade `active`; vincula con FK `zadarma_config_id` |
