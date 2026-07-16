# ADR 0007 — Commercial boundaries from product evidence

- Status: accepted
- Date: 2026-07-16

## Context

The platform needs reusable commercial capabilities, but the available products do not provide two equivalent current implementations.

Gestudio contains a current, tested financial model for academy obligations, payment allocations, credit, cash movements, receipts and stock. The current inventarios-muebleria backend contains authentication and commercial parties only. Its previous product, quote, sale, payment, cash and inventory modules exist only in a legacy commit before a backend replacement.

Several entities share fields while representing different business meanings. In particular, an academy student and a customer/supplier are not the same aggregate.

## Evidence baseline

- Gestudio: `3f314ba8cc61a71bfa434a46593cd02336ec16e5`
- inventarios-muebleria current: `b692017bd16fffb4c0cc63a1571f8a749c1636dc`
- inventarios-muebleria legacy commercial snapshot: `4e3a09007f8ab8b9c574438aab19332cbc7f605b`
- inventarios backend replacement boundary: `831a05d14175cc54c834d31aa22a807e4ff61021`

## Decision

1. Commercial extraction is evidence-driven. A capability requires two current proven uses before shared persistence is introduced.
2. Legacy code may contribute vocabulary and migration risks, but does not count as a second current implementation.
3. Shared commercial modules expose contracts and ports. They do not share source-product JPA entities.
4. `Alumno`, `ThirdParty` and legacy `ClienteProveedor` remain vertical profile aggregates.
5. The first shared commercial slice is a tenant-scoped Party Reference Directory.
6. A party reference contains only platform identity, source mapping, active state and display snapshot.
7. Academy guardianship/safety fields and commercial fiscal/demographic fields are excluded from the shared schema.
8. Historical financial and document rows keep issued display snapshots instead of dynamically rendering current profile data.
9. Products/services, pricing, quotes, receivables, payments, cash, credit, documents and inventory remain vertical or deferred until their individual evidence gates are satisfied.
10. Gestudio's guarantees for immutable obligations, explicit allocations, exact money, idempotency, deterministic locking and compensating reversals form the minimum acceptance floor for future shared financial capabilities.
11. Cash-register sessions are not a shared concept. Gestudio uses a movement ledger while legacy mueblería uses registers, sessions and mutable balances.
12. The current mueblería frontend/backend mismatch is a product repair concern, not a reason to restore deleted code into the platform core.
13. Platform tenancy, memberships, RBAC, entitlements, audit, idempotency and outbox are mandatory for all new commercial implementations.

## Party reference contract

```text
PartyRef
  tenantId
  partyId
  sourceType
  sourceId
  displayNameSnapshot

PartyDirectory
  requireActive(TenantContext, PartyId)
  find(TenantContext, PartyId)
  search(TenantContext, query, limit)
```

The mapping key is `(tenant, sourceType, sourceId)`. Import and reconciliation use the reliable command boundary and are idempotent.

## Financial extraction gate

A second current vertical must prove:

- immutable original obligations;
- explicit allocations;
- exact decimal and currency rules;
- no overapplication;
- explicit overpayment policy;
- append-only cancellation;
- deterministic lock ordering;
- idempotency and request-hash conflict;
- audit and transactional outbox;
- tenant isolation;
- PostgreSQL concurrency and reconciliation tests.

Until then, receivable/payment models remain vertical even where their conceptual names overlap.

## Consequences

- Shared commercial code grows more slowly but avoids premature generic models.
- Source products keep ownership of profile data.
- New commercial rows can reference parties consistently without importing vertical fields.
- Financial extraction will require a deliberately built second implementation rather than treating deleted code as current proof.
- Migration begins with mappings and reconciliation, not bulk profile copying.
- Some duplicate abstractions remain temporarily in verticals; this is preferable to an incorrect universal aggregate.

## Rejected alternatives

### Shared `Party` entity with optional fields

Rejected because it would become a nullable union of student, guardian, customer, supplier, person and company concerns.

### Copy all Gestudio financial tables into commercial-core

Rejected because one mature implementation does not prove universal persistence boundaries.

### Restore the legacy mueblería backend as shared code

Rejected because it is no longer active, is not tenant-scoped and has weaker financial invariants.

### Use the current mueblería frontend as parity evidence

Rejected because multiple pages call backend capabilities removed from the frozen current backend.

### Make cash sessions mandatory

Rejected because the current Gestudio cash model does not use them.

### Delay all sharing

Rejected because party references are a real cross-product need and can be shared without merging profile ownership.
