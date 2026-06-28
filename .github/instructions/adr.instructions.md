---
description: "Use when recording architecture decisions, evaluating technology choices, or creating new ADR documents. Covers ADR format, location, and when to create one."
---

# ADR Guidelines

Architecture Decision Records are stored in `docs/adr/`.

## Naming

`<number>-<short-description>.md`

Example: `001-usar-flyway-testcontainers.md`

## Format

Each ADR should include:

- **Estado**: Aceptado / Propuesto / Deprecado
- **Fecha**: ISO 8601 date
- **Contexto**: Why this decision is needed
- **Decisión**: What was decided
- **Consecuencias**: Positive and negative outcomes
- **Alternativas consideradas**: What was rejected and why

## When to create

Create an ADR when adopting a new technology, library, or architectural pattern that has lasting impact on the project.
