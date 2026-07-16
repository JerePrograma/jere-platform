# Repository Migration Strategy

## Objective

Consolidate proven capabilities from existing products without importing complete repositories, incompatible histories or duplicated domain models.

## Core rule

Migrate capabilities, not repositories.

An existing product remains authoritative until a coherent capability has been migrated, tested and adopted. Jere Platform must not become a collection of renamed source trees.

## Initial sources

### Gestudio

First source for identity, RBAC, audit, obligations, payments, cash, receipts, notifications, testing patterns and the academy vertical.

### inventarios-muebleria

Second source for catalog, inventory, parties, quotes, cash and commerce workflows. It will prove whether the commercial core is actually reusable.

### Specialized sources

- `GestorTurnosBarberia`: availability and appointments.
- `PresupuestadorFlete`: quoting and logistics.
- `Buscasitas---Web-Inmobiliaria`: listings and leads.
- `VoyageHub`: tourism portal concepts.
- `jr-prestamos-platform`: separately deployed financial product and integrity patterns.

Training projects, experiments and duplicated starters are reference-only unless a specific implementation is demonstrably superior.

## Capability record

Before migrating a capability, document:

```text
Capability
Source repository and commit
Current behavior and users
Inputs and outputs
Data model and invariants
Authorization and tenant assumptions
External integrations
Known defects
Existing tests
Target module
Acceptance criteria
Adoption and deprecation plan
```

## Canonical implementation criteria

When several repositories solve the same problem, compare:

1. proven business correctness;
2. automated tests;
3. security and tenant readiness;
4. data integrity;
5. maintainability;
6. stack compatibility;
7. documentation.

The selected implementation is evidence, not code that must be copied unchanged.

## Migration unit

Migrate complete vertical slices, such as:

- create a user, assign a role and authorize an operation;
- create a customer, generate an obligation, record a payment and issue a receipt;
- create a product, adjust stock and query the resulting inventory;
- create a discipline, enroll a student and generate a recurring charge.

Do not migrate isolated entities, utility folders or tables without an executable use case.

## Workflow

### Characterize

- inspect current behavior;
- capture invariants and edge cases;
- freeze the source commit used as reference;
- add characterization tests or explicit acceptance criteria.

### Design

- select the target module;
- define its public contracts;
- identify tenant, permission and entitlement requirements;
- document intentional behavior changes;
- add an ADR for durable architectural decisions.

### Implement

- migrate the smallest coherent slice;
- adapt naming to platform language;
- use forward-only database migrations;
- include audit and operational evidence.

### Validate

- run unit and integration tests;
- prove tenant isolation;
- prove authorization and entitlement behavior;
- reconcile domain outcomes with the source;
- document known differences.

### Adopt

Use an explicit rollout strategy: direct replacement, adapter-based migration, shadow comparison or tenant-by-tenant rollout.

### Deprecate

- stop extending the duplicated implementation;
- document the platform replacement;
- remove old code only after adoption;
- archive obsolete repositories when no active product depends on them.

## Data rules

- Use anonymized, repeatable fixtures.
- Make import processes idempotent and restartable.
- Assign tenant ownership explicitly.
- Preserve traceability where financial or audit history requires it.
- Produce reconciliation reports for counts, balances and rejected records.

## Anti-patterns

- Copying an entire backend and renaming packages.
- Combining unrelated migration histories into one sequence.
- Sharing mutable persistence entities across modules.
- Keeping two active implementations indefinitely.
- Representing domain differences through nullable columns and many flags.
- Migrating a capability before identifying a real product use case.

## First sequence

1. Repository foundation and CI.
2. Tenant context and organization model.
3. Identity and session lifecycle.
4. Authorization and entitlements.
5. Audit and transactional outbox.
6. Parties and contact model.
7. Receivables, payments and receipts from Gestudio.
8. Academy vertical.
9. Catalog and inventory from inventarios-muebleria.
10. Second product shell proving reuse.
