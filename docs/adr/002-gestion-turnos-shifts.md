# ADR-002: Gestión de Turnos (Shifts) — CRUD, Solapamientos y División Automática

**Estado**: Aceptado
**Fecha**: 2026-06-28

## Contexto

El proyecto necesita gestionar turnos de guardia (`Shifts`) que son rangos de tiempo asignados a una persona dentro de un grupo de guardia. Los requisitos incluyen:

- CRUD básico de turnos con filtros por persona, grupo y rango de fechas
- Evitar que una misma persona tenga turnos solapados
- Permitir que una persona ceda parcialmente un turno a otra (favores/reemplazos), dividiendo el turno original automáticamente
- Al modificar un turno (extender/acortar), ajustar los turnos adyacentes de la misma persona para mantener la continuidad

La entidad `Shift` y la tabla `shifts` ya existían desde la V1 del esquema, pero no tenían capa de aplicación (repository, service, controller, DTOs, mapper).

## Decisión

### 1. CRUD con filtros por query params

Se implementa `GET /api/v1/shifts` con parámetros opcionales:

- `personId` — filtrar por persona
- `onCallGroupId` — filtrar por grupo de guardia
- `from` / `to` — filtrar por rango de fechas (ISO 8601)

El repositorio NO usa la estrategia `:param IS NULL OR col = :param` en JPQL porque PostgreSQL no puede inferir el tipo de datos de parámetros `UUID`/`TIMESTAMP` cuando son `NULL`, lanzando `PSQLException: could not determine data type of parameter`. En su lugar, el servicio selecciona la query adecuada según qué parámetros vienen informados, aplicando filtrado adicional en memoria cuando es necesario.

### 2. Validación de solapamientos (misma persona → 409)

Al **crear** un turno, se verifica que la misma persona no tenga ya un turno en el rango `[startTime, endTime]` mediante una query JPQL de intersección temporal:

```sql
SELECT s FROM Shift s
WHERE s.person.id = :personId
  AND s.startTime < :endTime
  AND s.endTime > :startTime
```

Si hay solapamiento, se lanza `ShiftOverlapException` → `GlobalExceptionHandler` responde con **409 Conflict**.

### 3. División automática (distinta persona)

Al crear un turno para la Persona B en un rango que solapa con turnos existentes de la Persona A (u otras), el sistema **divide automáticamente** el turno de la Persona A en hasta 2 partes:

- **Tramo delantero** (antes del solapamiento): se acorta el turno original de A ajustando `endTime = B.startTime`
- **Tramo trasero** (después del solapamiento): se crea un nuevo turno para A con `startTime = B.endTime, endTime = A.endTime`
- **Solapamiento total** (B engulle A): se elimina el turno A
- **Solapamiento contenido** (A contiene a B): ocurren ambos ajustes (delantero + trasero)

No existe un endpoint separado de "intercambio" — la división ocurre automáticamente como parte del `create()`.

### 4. Ajuste de turnos adyacentes en update

Al **actualizar** un turno (cambiar `startTime` o `endTime`), **no se lanza 409** por solapamiento con la misma persona. En su lugar:

- Si se **extiende** el turno → el siguiente turno de la misma persona retrocede su `startTime`
- Si se **acorta** el turno → el siguiente turno avanza su `startTime` (rellena el hueco)
- Si se mueve el **inicio** hacia atrás → el turno anterior retrocede su `endTime`
- Si se mueve el **inicio** hacia adelante → el turno anterior avanza su `endTime`

Si el ajuste deja un turno inválido (`startTime >= endTime`), ese turno se elimina.

Si el nuevo rango solapa con turnos de **otras personas**, aplica la misma lógica de división que en creación.

## Consecuencias

- **Positivas**:
  - No se necesita un endpoint separado para favores/reemplazos — ocurre automáticamente al crear turnos
  - Los turnos de una misma persona siempre mantienen continuidad sin necesidad de intervención manual
  - Comportamiento predecible: el turno más específico (el que se crea/actualiza) tiene prioridad
- **Negativas**:
  - La lógica de división y ajuste está acoplada al `create()`/`update()`, lo que aumenta la complejidad del servicio
  - El filtrado en memoria para combinaciones parciales de parámetros puede ser ineficiente con grandes volúmenes de datos (escalable vía `Specification` de Spring Data JPA si es necesario)
  - La división automática puede generar múltiples turnos para una misma persona en operaciones atómicas

## Alternativas consideradas

| Alternativa | Rechazada por |
|---|---|
| Endpoint separado `POST /api/v1/shifts/exchange` | Duplica lógica de validación; el comportamiento deseado es automático al crear el turno |
| Permitir solapamientos y resolver en facturación | Complejidad oculta: los turnos solapados no reflejan la realidad de las guardias |
| Generación automática de horarios | El cliente prefiere crear turnos manualmente; la generación no aporta valor en este sprint |
| Ignorar ajuste adyacente en update | Dejaría huecos o solapamientos entre turnos de la misma persona, requiriendo corrección manual |
