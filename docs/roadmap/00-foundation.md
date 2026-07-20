# Phase 0: Foundation Roadmap

## Goal

Create an executable, testable platform skeleton with enforceable module boundaries before migrating product behavior.

## Verified status

Phase 0 and the shared Phase 1 kernel are complete on `main` at
`53972cc2b15a1c4d1f49ac0724e1ba95c2bd29d8`. Main CI run `29752682676`
passed on 2026-07-20.

This roadmap records executable gates. A documented context is not an implemented capability.

## Exit criteria

Phase 0 is complete when:

- backend and frontend workspaces build locally and in CI;
- PostgreSQL starts through Docker Compose;
- Flyway applies a clean baseline;
- architecture tests reject forbidden module dependencies;
- tenant context is represented in contracts, even before complete persistence enforcement;
- test fixtures and validation commands are documented;
- no production product is forced to migrate prematurely.

## Work packages

### F0.1 Repository conventions

- finalize language and package naming;
- define branch and commit conventions;
- add contribution and security guidance;
- decide the code license before publishing proprietary implementation.

### F0.2 Backend skeleton

- create a Maven multi-module build;
- create `platform-api` and shared module parents;
- configure Java 21 and Spring Boot;
- configure formatting, static analysis and test coverage;
- add Testcontainers baseline.

Acceptance:

- one command compiles and tests all backend modules;
- the application starts with a health endpoint;
- no business entities are introduced.

### F0.3 Frontend skeleton

- create a workspace for applications and shared packages;
- configure React, TypeScript and Vite;
- add linting, formatting and tests;
- create a minimal accessible application shell.

Acceptance:

- one command validates all frontend workspaces;
- product applications can consume the shared UI package;
- the shell contains no speculative product navigation.

### F0.4 Local infrastructure

- PostgreSQL through Docker Compose;
- documented environment-variable contract;
- example configuration without secrets;
- repeatable database reset and migration commands.

Acceptance:

- a new checkout starts from documented commands;
- no local absolute paths are required;
- secrets remain outside version control.

### F0.5 CI

- backend build and tests;
- frontend build and tests;
- dependency and secret scanning;
- migration validation;
- pull-request checks with caching.

Acceptance:

- the default branch cannot silently accept a broken build;
- checks are deterministic enough for agent-driven work.

### F0.6 Architecture enforcement

- define module dependency rules;
- add ArchUnit or equivalent tests;
- document public versus internal packages;
- reject cross-module persistence access.

Acceptance:

- a deliberately forbidden dependency fails tests;
- the rule is visible in CI output.

## Not part of Phase 0

- subscription billing;
- product migrations;
- external messaging providers;
- microservices;
- Kubernetes;
- generic workflow engines.

## Phase 1 — shared platform kernel

Completed through PRs #38-#41:

- tenant, organization, branch and membership lifecycle;
- identity, credential and revocable session lifecycle;
- tenant-scoped roles, permissions, entitlements and feature flags;
- append-only audit, reliable commands and transactional outbox.

Exit evidence: PostgreSQL tenant-boundary and authorization tests, authentication lifecycle tests, reliability concurrency/recovery tests and ADRs 0003-0006.

## Milestone 2 — evidence-based commercial boundary

| Increment | State | Evidence |
|---|---|---|
| M2.1 compare Gestudio and inventarios-muebleria | COMPLETE | issue #28, PR #42, ADR 0007 |
| M2.2 tenant Party Reference Directory | COMPLETE | issue #43, PR #44, ADR 0008, Flyway V7 |
| M2.3a signed artifact ingestion | IMPLEMENTED | issues #56/#59, ADRs 0009/0010, v1 schema, V8 and integration tests |
| M2.3b Gestudio source-owned emitter | IN PROGRESS | platform #51, Gestudio #14; local cross-repository validation |
| M2.3c Scalaris source-owned emitter | BLOCKED | explicit tenant mapping is not defined |

M2.3 remains a reference-only integration. The platform can authenticate, dry-run
and atomically ingest resumable artifacts without false multipage absences. The
Gestudio emitter is not complete until both PRs and their exact heads pass CI and
merge. No adapter may connect Jere Platform directly
to source databases or copy email, document, address, guardian, tax or
commercial-profile fields.

## Later milestones

These are ordered gates, not simultaneous workstreams:

1. Merge and operationally stage the Gestudio M2.3 emitter after green CI.
2. Define Scalaris tenant mapping before starting its emitter.
3. Prove one tenant-scoped CRM/contact flow only if product evidence requires more than Party References.
4. Prove catalog/pricing compatibility in two current products before shared persistence.
5. Add a complete quote or inventory vertical slice with authorization, tenancy, audit and tests.
6. Revisit financial extraction only after every ADR 0007 integrity gate has two current implementations.
7. Add documents/notifications through the outbox when a real vertical needs them.
8. Add SaaS subscriptions only after product packaging and billing ownership are decided.

## Cross-cutting gates

- Keep the repository free of secrets, real data and proprietary customer logic.
- Preserve shared-database tenant isolation and negative cross-tenant tests.
- Use forward-only Flyway migrations.
- Keep backend and frontend contracts synchronized.
- Require local supported validation and CI success for the exact head SHA.
- Do not extract a service until ADR 0001 prerequisites are demonstrated.
