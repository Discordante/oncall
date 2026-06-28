# Arquitectura

## Decisión: Capas tradicional (Controller → Service → Repository)

Se opta por una arquitectura en capas clásica de Spring Boot por su simplicidad, rápido onboarding y adecuación al dominio del proyecto.

## Diagrama de capas

```
┌──────────────────────────────────┐
│        Controller (REST)         │  ← HTTP, validación de entrada
├──────────────────────────────────┤
│          DTO (Request/Response)   │  ← Objetos de transferencia
├──────────────────────────────────┤
│          Service (lógica)         │  ← Reglas de negocio, transacciones
├──────────────────────────────────┤
│         Mapper (Entity ↔ DTO)     │  ← Conversión entre capas
├──────────────────────────────────┤
│         Repository (BD)           │  ← Spring Data JPA
├──────────────────────────────────┤
│        Entity (JPA)               │  ← Modelo de persistencia
└──────────────────────────────────┘
```

## Reglas estrictas

| Desde | Hacia | Permiso |
|---|---|---|
| Controller | Service | ✓ |
| Controller | DTO | ✓ |
| Service | Repository | ✓ |
| Service | Entity | ✓ |
| Service | Mapper | ✓ |
| Service | DTO | ✓ |
| Repository | Entity | ✓ |
| Entity | — | ✗ (ninguna) |
| DTO | Entity | ✗ (nunca) |
| Controller | Repository | ✗ |

## Mecanismos transversales

- **Exception handling**: `@RestControllerAdvice` global en `exception/`
- **Validación**: Jakarta Validation `jakarta.validation.constraints` en DTOs
- **Logging**: Lombok `@Slf4j`
- **Mapper**: conversión manual con métodos estáticos o MapStruct en cada mapper
