---
description: "Use when designing new components, services, or restructuring package layout. Covers layered architecture rules, dependency direction, and package conventions for the Oncall project."
---

# Architecture Guidelines

## Layered architecture

```
┌──────────────────────────────┐
│     Controller (REST)        │  ← HTTP, input validation
├──────────────────────────────┤
│       DTO (Request/Response)  │  ← Transfer objects
├──────────────────────────────┤
│       Service (business)     │  ← Business rules, transactions
├──────────────────────────────┤
│      Mapper (Entity ↔ DTO)   │  ← Layer conversion
├──────────────────────────────┤
│      Repository (DB)         │  ← Spring Data JPA
├──────────────────────────────┤
│     Entity (JPA)             │  ← Persistence model
└──────────────────────────────┘
```

## Strict dependency rules

| From | To | Allowed |
|---|---|---|
| Controller | Service | ✓ |
| Controller | DTO | ✓ |
| Service | Repository | ✓ |
| Service | Entity | ✓ |
| Service | Mapper | ✓ |
| Service | DTO | ✓ |
| Repository | Entity | ✓ |
| Entity | — | ✗ (none) |
| DTO | Entity | ✗ (never) |
| Controller | Repository | ✗ |

## Cross-cutting concerns

- **Exception handling**: Global `@RestControllerAdvice` in `exception/` package
- **Validation**: Jakarta Validation annotations on DTOs
- **Logging**: Lombok `@Slf4j`
- **Mapping**: Static methods or MapStruct in each mapper class

See [docs/architecture.md](/docs/architecture.md) for full details.
