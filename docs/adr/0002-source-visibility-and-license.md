# ADR 0002: Public Foundation with Proprietary License

- Status: Accepted
- Date: 2026-07-16

## Context

The repository was created as public before deciding how the shared platform and commercial verticals would be distributed. The platform is intended to support proprietary SaaS products and may eventually contain business logic extracted from active systems.

Public visibility exposes source code even when no reuse permission is granted.

## Decision

During the foundation phase:

- the repository remains publicly visible;
- the source is distributed under an all-rights-reserved proprietary license;
- only generic architecture, tooling and non-sensitive runtime scaffolding may be committed;
- customer-specific and commercially sensitive implementations are prohibited;
- vertical business logic may be migrated only after the repository becomes private or a deliberate public/private split is implemented.

## Consequences

### Positive

- Foundation work remains inspectable.
- Ownership and reuse permissions are explicit.
- No open-source commitment is made accidentally.

### Negative

- Source remains readable by third parties.
- Proprietary vertical migration is blocked while the repository is public.
- External contributors need written permission before reusing code.

## Revisit trigger

Revisit this ADR before the first migration of Gestudio, commerce, appointments, loans, health or tax implementation.
