# API REST — Guía de diseño

## URL structure

```
/api/v1/<resource>[/<id>][/<sub-resource>]
```

Ejemplos:
```
GET    /api/v1/teams
POST   /api/v1/teams
GET    /api/v1/teams/{id}
PUT    /api/v1/teams/{id}
DELETE /api/v1/teams/{id}
GET    /api/v1/teams/{id}/members
```

## HTTP methods

| Método | Acción | Idempotente | Códigos |
|---|---|---|---|
| `GET` | Obtener recurso(s) | ✓ | 200, 404 |
| `POST` | Crear recurso | ✗ | 201, 400, 409 |
| `PUT` | Reemplazar recurso | ✓ | 200, 201, 204, 400, 404 |
| `PATCH` | Actualizar parcial | ✗ | 200, 400, 404 |
| `DELETE` | Eliminar recurso | ✓ | 204, 404 |

## Request/Response

### Formato general

```json
{
  "data": { ... },
  "errors": null
}
```

### Errores

```json
{
  "data": null,
  "errors": [
    {
      "code": "VALIDATION_ERROR",
      "message": "El campo 'name' es obligatorio",
      "field": "name"
    }
  ]
}
```

### Paginación

```json
// GET /api/v1/teams?page=0&size=20
{
  "data": [ ... ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

## Convenciones

- **Controller**: `@RequestMapping("/api/v1/teams")` a nivel de clase
- **Respuestas**: ResponseEntity<T> o DTO directo
- **DTO de entrada**: anotaciones Jakarta Validation
- **DTO de salida**: sin referencias circulares, solo lo necesario
- **Códigos de estado semánticos**: no devolver siempre 200
- **Versionado**: por URL prefix `/api/v1/`, `/api/v2/`
- **Date format**: ISO 8601 (`2026-06-23T10:30:00Z`)
