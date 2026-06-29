# Integración Zadarma — Redirección de llamadas VoIP

## Descripción general

Oncall se integra con la API de Zadarma para redirigir automáticamente las llamadas entrantes al teléfono de la persona que está de guardia **en ese momento**. La redirección se activa cuando comienza un turno y se actualiza cuando termina, saltando al siguiente turno activo o desactivándose si no hay nadie de guardia.

La integración usa directamente la API HTTP de Zadarma (`api.zadarma.com/v1/`) a través de `RestClient` de Spring Boot, siguiendo el mismo patrón que la integración de Telegram.

**Solo se necesita 1 llamada a la API por redirección** (`PUT /v1/sip/redirection/`), no el antiguo flujo de 3 llamadas que usaba el script `oncall.sh`.

---

## Arquitectura

```
┌──────────────────────────────────────────────────────────────────┐
│                        Capas del proyecto                         │
├──────────────────────────────────────────────────────────────────┤
│  zadarma/           ← Nuevo paquete para la integración VoIP      │
│  ├── client/        ← ZadarmaApiClient (HTTP + firma HMAC-SHA1)  │
│  ├── service/       ← ZadarmaService / ZadarmaServiceImpl        │
│  ├── scheduler/     ← CallRedirectionScheduler (núcleo temporal)  │
│  └── event/         ← ZadarmaRedirectFailedEvent + AlertListener │
│                                                                   │
│  entity/            ← OnCallGroup (+ zadarmaSipId, redir. type)  │
│                      ← Shift (+ redirectStatus, intentos, etc.)   │
│  dto/               ← DTOs actualizados con campos Zadarma        │
│  mapper/            ← Mappers actualizados                        │
│  service/           ← ShiftServiceImpl (engancha con scheduler)   │
│  exception/         ← ZadarmaApiException + handler 502           │
│  config/            ← ZadarmaConfig (@ConfigurationProperties)    │
└──────────────────────────────────────────────────────────────────┘
```

### Paquete `zadarma/` (8 archivos)

| Subpaquete | Archivo | Propósito |
|---|---|---|
| `client/` | `ZadarmaApiClient.java` | Cliente HTTP con autenticación HMAC-SHA1 |
| `client/` | `ZadarmaResponse.java` | Record genérico para respuestas de la API |
| `service/` | `ZadarmaService.java` | Interfaz: `redirectToPerson`, `disableRedirection`, `getCurrentRedirectionDestination`, `normalizePhoneNumber` |
| `service/` | `ZadarmaServiceImpl.java` | Implementación: valida SIP, normaliza teléfonos, llama a la API |
| `scheduler/` | `CallRedirectionScheduler.java` | **El núcleo**: scheduler por eventos, recuperación tras reinicio, fallback periódico, reintentos |
| `event/` | `ZadarmaRedirectFailedEvent.java` | Evento publicado cuando falla una redirección |
| `event/` | `ZadarmaRedirectAlertListener.java` | Envía alertas de Telegram cuando falla una redirección |

### Archivos relacionados fuera de `zadarma/`

| Archivo | Cambio |
|---|---|
| `config/ZadarmaConfig.java` | Propiedades `zadarma.api.*` y `zadarma.redirect.*` |
| `entity/OnCallGroup.java` | + `zadarmaSipId`, `zadarmaRedirectionType` |
| `entity/Shift.java` | + `redirectStatus`, `redirectFailedAt`, `redirectLastAttemptAt`, `redirectRetryCount` |
| `dto/OnCallGroupRequest.java` | + `zadarmaSipId`, `zadarmaRedirectionType` (opcionales) |
| `dto/OnCallGroupResponse.java` | + `zadarmaSipId`, `zadarmaRedirectionType` |
| `dto/ShiftResponse.java` | + `redirectStatus` |
| `mapper/OnCallGroupMapper.java` | Mapea los nuevos campos en ambas direcciones |
| `mapper/ShiftMapper.java` | Mapea `redirectStatus` en la respuesta |
| `repository/ShiftRepository.java` | + 5 queries: turnos activos, futuros, siguiente turno, por estado |
| `service/ShiftServiceImpl.java` | Programa transiciones al crear/actualizar/borrar turnos |
| `exception/ZadarmaApiException.java` | Excepción unchecked para errores de API |
| `exception/GlobalExceptionHandler.java` | Handler `@ExceptionHandler` → 502 BAD GATEWAY |

---

## API de Zadarma utilizada

### Endpoints

| Método | Endpoint | Uso |
|---|---|---|
| `PUT` | `/v1/sip/redirection/` | Activar/Modificar redirección a un teléfono |
| `GET` | `/v1/sip/redirection/` | Consultar estado actual (solo para el checker de idempotencia) |

### Flujo recomendado vs. flujo antiguo

| Antiguo script `oncall.sh` | Nueva integración |
|---|---|
| 3 llamadas: PUT + GET numbers + GET status | **1 llamada**: PUT |
| Las llamadas GET eran para mostrar info al operador CLI | El PUT ya devuelve `{status:"success", destination:"..."}` |
| El GET de estado se usa solo en el fallback checker (evitar PUTs redundantes) | — |

### Autenticación

Cada petición incluye una cabecera `Authorization` con firma HMAC-SHA1:

```
Authorization: <KEY>:<Base64(HMAC-SHA1(SECRET, método + queryString + MD5(queryString)))>
```

Algoritmo:
1. Ordenar parámetros alfabéticamente por clave
2. Construir query string (RFC 1738)
3. Calcular `MD5(queryString)`
4. Concatenar: `métodoHTTP + queryString + md5`
5. Firmar con HMAC-SHA1 usando el API secret
6. Codificar en Base64
7. Anteponer `KEY:`

---

## Base de datos

### Migración

| Migración | Archivo | Cambios |
|---|---|---|
| V6 | `V6__add_zadarma_integration.sql` | Columnas Zadarma en `on_call_groups` y `shifts` |

### Columnas añadidas

**Tabla `on_call_groups`**

| Columna | Tipo | Descripción |
|---|---|---|
| `zadarma_sip_id` | `VARCHAR(255)` | ID del SIP en Zadarma para este grupo (ej. `"921495"`). Si es null, el grupo no usa redirección. |
| `zadarma_redirection_type` | `VARCHAR(10) DEFAULT 'sip'` | Tipo de redirección: `sip` o `pbx`. Solo `sip` implementado actualmente. |

**Tabla `shifts`**

| Columna | Tipo | Descripción |
|---|---|---|
| `redirect_status` | `VARCHAR(20)` | Estado de la redirección: `null` (pendiente), `ok`, `failed` |
| `redirect_failed_at` | `TIMESTAMPTZ` | Cuándo falló la redirección por última vez |
| `redirect_last_attempt_at` | `TIMESTAMPTZ` | Cuándo se intentó la redirección por última vez |
| `redirect_retry_count` | `INT DEFAULT 0` | Número de reintentos acumulados para este turno |

---

## Ciclo de vida de la redirección

### 1. Creación de un turno futuro

```
POST /api/v1/shifts { startTime: "2026-07-10T00:00:00Z", ... }
  → Shift guardado en BD (redirectStatus = null)
  → CallRedirectionScheduler programa dos tareas:
      - A las 2026-07-10T00:00:00Z → PUT /v1/sip/redirection/
      - A las 2026-07-17T08:00:00Z → manejar fin de turno
  → NO se llama a la API de Zadarma todavía
```

### 2. Llegada de la hora de inicio

```
TaskScheduler ejecuta redirectToOnCallPerson(shiftId)
  → PUT /v1/sip/redirection/ { id: "921495", type: "phone", number: "34653177761" }
  → Éxito: redirectStatus = "ok"
  → Fallo: redirectStatus = "failed", reintento en 30s, alerta Telegram
```

### 3. Fin del turno

```
TaskScheduler ejecuta handleShiftEnd(shiftId)
  → Busca el siguiente turno activo para el mismo grupo
  → Si existe → redirige a la siguiente persona
  → Si no existe → desactiva la redirección (PUT con status=off)
```

### 4. Recuperación tras reinicio del servidor

```
ApplicationReadyEvent → onStartup()
  → Consulta todos los turnos activos y futuros de la BD
  → Turnos activos ahora → redirige inmediatamente
  → Turnos futuros → reprograma sus transiciones
```

### 5. Fallback periódico (cada 5 minutos)

```
@Scheduled(fixedDelay = 300000)
  → Consulta todos los turnos actualmente activos
  → Para cada uno: GET /v1/sip/redirection/?id=...
  → Si el destino no coincide con el teléfono esperado → corrige (PUT)
  → También reintenta turnos con redirectStatus = "failed"
```

---

## Configuración

### Variables de entorno

```bash
export ZADARMA_API_KEY=your_api_key
export ZADARMA_API_SECRET=your_api_secret
```

### application.yml

```yaml
zadarma:
  api:
    key: ${ZADARMA_API_KEY:}
    secret: ${ZADARMA_API_SECRET:}
    base-url: https://api.zadarma.com
  redirect:
    retry-max-attempts: 10
    retry-initial-delay-seconds: 30
    fallback-check-interval-seconds: 300
```

### Configurar un grupo para usar Zadarma

Al crear o actualizar un `OnCallGroup`, asignar el SIP ID:

```json
POST /api/v1/on-call-groups
{
  "name": "Crew OnCall",
  "grossPricePerDay": "150.00",
  "zadarmaSipId": "921495",
  "zadarmaRedirectionType": "sip"
}
```

- `zadarmaSipId`: el ID del SIP en el panel de Zadarma (obtenido de `GET /v1/sip/` o del área personal)
- `zadarmaRedirectionType`: `"sip"` (por defecto). En el futuro soportará `"pbx"`.

Un grupo sin `zadarmaSipId` simplemente no usa redirección (compatible hacia atrás).

---

## Flujo de error y reintentos

Cuando una redirección falla:

1. El turno se marca con `redirectStatus = "failed"` y se registra la hora del fallo
2. Se incrementa `redirectRetryCount`
3. Se publica `ZadarmaRedirectFailedEvent`
4. El listener envía una alerta al chat de Telegram del grupo:
   ```
   ⚠️ Fallo en la redirección de llamadas
   📋 Grupo: Crew OnCall
   👤 Persona de guardia: Alice
   📞 Teléfono: ***7761
   ❌ Error: Zadarma API returned error
   🔄 Reintento: 1/10
   ```
5. Se programa un reintento con retardo creciente: 30s → 60s → 90s → ... hasta 10 intentos
6. Agotados los reintentos, el fallback checker (cada 5 min) sigue intentándolo

**La redirección anterior se mantiene activa** mientras se reintenta — las llamadas nunca se pierden, simplemente van al último destino configurado correctamente.

---

## Normalización de números de teléfono

Antes de enviar un número a Zadarma, se normaliza:

- Se eliminan: `+`, espacios, guiones, paréntesis, puntos
- Se valida que solo contenga dígitos
- Se valida longitud entre 7 y 15 dígitos (estándar E.164)

Ejemplos:
- `+34 653 177 761` → `34653177761`
- `0034 653 177 761` → `0034653177761`
- `653-177-761` → `653177761`

Si una persona no tiene `phoneNumber` configurado, la redirección falla y se alerta.

---

## Monitorización

La API de Zadarma tiene un límite de **100 peticiones/minuto**. El cliente registra la cabecera `X-RateLimit-Remaining` en logs para observabilidad.

Para verificar conectividad con Zadarma manualmente, se puede usar el endpoint de balance:

```bash
curl -H "Authorization: <KEY>:<SIGNATURE>" https://api.zadarma.com/v1/info/balance/?format=json
```
