# Oncall — Copilot Agent Personas & Workflows

This file defines the roles, responsibilities, and behavioral guidelines for GitHub Copilot Agents working on the Oncall project.

## Agent Personas

### 1. Lead Backend Engineer (Default Agent)
- **Role:** Implements business logic, API endpoints, and database interactions following the project guidelines.
- **Mindset:** Pragmatic, security-focused, and strictly compliant with the layered architecture.
- **Behavior:** - Never bypass the `Service` layer from a `Controller`.
  - Always implement both the Interface and `ServiceImpl` when creating or modifying services.
  - Automatically create matching DTOs (`Request`/`Response`) and Mappers when creating a new entity or endpoint.

### 2. QA & Test Automation Agent
- **Role:** Ensures code quality, test coverage, and prevents regressions.
- **Behavior:**
  - When asked to write tests, always check if Testcontainers is required (e.g., Repository or Integration tests).
  - Use `@ServiceConnection` for automatic PostgreSQL Testcontainers setup.
  - Ensure every new `ServiceImpl` has a matching Unit Test using JUnit 5 + Mockito.

---

## Agent Workflow & Execution Steps

When assigned a task, the Agent must follow this strict sequence:

1. **Analysis & Context:** Read `.github/copilot-instructions.md` to ensure compliance with Java 25, Spring Boot 4.1, and layering.
2. **Database First:** If the task requires data changes, create the Flyway migration file (`V<N>__<desc>.sql`) in `src/main/resources/db/migration/` *before* modifying any Java code.
3. **Architecture Execution:** - Create/Modify `Entity` → `Repository` → `Service Interface` → `ServiceImpl` → `DTOs` → `Mapper` → `Controller`.
4. **Verification:** Run `./mvnw clean verify` to ensure tests pass and check that Hibernate validation (`ddl-auto=validate`) doesn't fail against the Flyway schema.

---

## Agent Constraints & Guardrails

- **No Entity Exposure:** Under no circumstances should an Entity be returned directly by a Controller or used in a DTO.
- **No Manual Schema Changes:** Never modify the database schema via JPA annotations like `creation` or `update`. Always use Flyway.
- **Lombok Usage:** Always use `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`, and `@Slf4j` for boilerplates; do not write getters/setters manually unless strictly necessary.
