---
description: "Use when creating or modifying REST endpoints, DTOs, or API contracts. Covers URL structure, HTTP methods, response format, pagination, and error handling conventions."
---

# API Guidelines

## URL structure

```
/api/v1/<resource>[/<id>][/<sub-resource>]
```

## HTTP methods

| Method | Action | Idempotent | Status codes |
|---|---|---|---|
| `GET` | Retrieve | ✓ | 200, 404 |
| `POST` | Create | ✗ | 201, 400, 409 |
| `PUT` | Replace | ✓ | 200, 201, 204, 400, 404 |
| `PATCH` | Partial update | ✗ | 200, 400, 404 |
| `DELETE` | Delete | ✓ | 204, 404 |

## Response format

### Success
```json
{
  "data": { ... },
  "errors": null
}
```

### Errors
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

### Pagination
```json
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

## Conventions

- **Controller**: `@RequestMapping("/api/v1/teams")` at class level
- **Responses**: `ResponseEntity<T>` or direct DTO
- **Input DTOs**: Jakarta Validation annotations
- **Output DTOs**: no circular references, only necessary fields
- **Semantic status codes**: don't always return 200
- **Versioning**: URL prefix `/api/v1/`, `/api/v2/`
- **Dates**: ISO 8601 (`2026-06-23T10:30:00Z`)

See [docs/api-guidelines.md](/docs/api-guidelines.md) for full details.
