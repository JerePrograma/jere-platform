# AGENTS.md

## Mission

Build Jere Platform as the canonical technical foundation for multiple SaaS verticals while preserving product focus, operational simplicity and strong module boundaries.

## Current phase

Foundation only. Do not migrate production code, create microservices or redesign product domains until the relevant architecture and migration documents exist.

## Required architecture

- Monorepo.
- Modular monolith for the main platform API.
- Java 21 and Spring Boot for backend applications.
- PostgreSQL and Flyway for persistence.
- React, TypeScript and Vite for web applications.
- Multi-tenancy enforced from request context through persistence.
- Internal modules communicate through explicit application interfaces or domain events.
- External asynchronous work uses a transactional outbox before introducing infrastructure-heavy brokers.

## Module layers

Each backend module should expose only intentional contracts and use this internal shape where practical:

```text
<module>/
├── api/             # public contracts and inbound adapters
├── application/     # use cases and orchestration
├── domain/          # business rules and aggregates
├── infrastructure/  # persistence and outbound adapters
└── internal/        # package-private implementation details
```

## Non-negotiable rules

1. Do not import an existing repository wholesale.
2. Do not share JPA entities across module boundaries.
3. Do not access another module's tables directly.
4. Do not add a generic abstraction until at least two real use cases prove it.
5. Do not extract a microservice without a documented operational reason.
6. Do not commit secrets, real customer data, database dumps or private environment files.
7. Every tenant-owned table must include tenant isolation and tenant-aware uniqueness.
8. Permissions, entitlements, feature flags and configuration are separate concepts.
9. Financial writes require idempotency and compensating records; avoid destructive mutation of financial history.
10. Migrations are forward-only after release.

## Source repositories

Existing repositories are evidence and migration sources, not permanent dependencies:

- `JerePrograma/Gestudio`
- `JerePrograma/inventarios-muebleria`
- `JerePrograma/GestorTurnosBarberia`
- `JerePrograma/PresupuestadorFlete`
- `JerePrograma/jr-prestamos-platform`

Before migrating a capability:

1. Inventory the current behavior.
2. Identify the canonical implementation.
3. Add characterization tests or explicit acceptance criteria.
4. Define the target module contract.
5. Migrate the minimum coherent slice.
6. Validate functional parity.
7. Deprecate the duplicated implementation.

## Change workflow

- Work from an issue or a documented phase objective.
- Use branches named `agent/<short-description>` for agent-generated work.
- Keep commits atomic and descriptive.
- Open draft pull requests by default.
- Include rationale, affected modules, validation and migration impact in the PR body.
- Add or update an ADR when changing a durable architectural decision.

## Definition of done

A change is not complete merely because it compiles. Relevant changes must include:

- automated tests;
- tenant-boundary tests where data is tenant-owned;
- authorization tests where access is restricted;
- migration validation where schema changes;
- documentation of public contracts;
- no leaked secrets or local configuration;
- a rollback or forward-recovery strategy for operationally significant changes.

## Scope discipline

The platform exists to accelerate validated products. Do not build speculative modules merely because they might be reusable. Prefer one complete vertical slice over several unfinished frameworks.
