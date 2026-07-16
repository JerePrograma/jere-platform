# Phase 0: Foundation Roadmap

## Goal

Create an executable, testable platform skeleton with enforceable module boundaries before migrating product behavior.

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

## Deferred until Phase 1

- complete authentication;
- production RBAC;
- subscription billing;
- product migrations;
- external messaging providers;
- microservices;
- Kubernetes;
- generic workflow engines.

## Phase 1 preview

Phase 1 establishes the kernel:

1. tenant and organization lifecycle;
2. identity and session lifecycle;
3. roles, permissions and scopes;
4. entitlements and module activation;
5. audit trail;
6. transactional outbox.

No commercial-core migration should begin until tenant and authorization boundaries are enforceable.
