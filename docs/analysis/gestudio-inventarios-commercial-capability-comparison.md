# Gestudio vs. inventarios-muebleria — commercial capability comparison

- Status: accepted planning baseline
- Date: 2026-07-16
- Tracking issue: #28
- Target platform baseline: `JerePrograma/jere-platform@74ef83efba778835867e185f9f923e856d464b68`

## 1. Frozen evidence

This analysis does not use a moving `main` branch as evidence.

| Source | Frozen commit | Meaning |
|---|---|---|
| Gestudio | `3f314ba8cc61a71bfa434a46593cd02336ec16e5` | Current, active product implementation and primary source for academy and financial invariants. |
| inventarios-muebleria | `b692017bd16fffb4c0cc63a1571f8a749c1636dc` | Current repository state. Contains authentication and parties, but no active commercial backend modules. |
| inventarios-muebleria legacy commercial snapshot | `4e3a09007f8ab8b9c574438aab19332cbc7f605b` | Historical evidence immediately before the backend replacement that removed products, cash, quotes, sales and inventory. It is not current production code. |
| inventarios-muebleria refactor boundary | `831a05d14175cc54c834d31aa22a807e4ff61021` | Commit that removed the old commercial backend and added `com.scalaris` authentication and parties. |

### Evidence grades

- **Current and proven**: present in a frozen current commit and backed by meaningful invariants/tests.
- **Current but unproven**: present in a frozen current commit, with incomplete tests or weak operational guarantees.
- **Legacy evidence only**: present only before the refactor boundary.
- **Absent**: not present in the current frozen implementation.

Legacy evidence may inform vocabulary and migration risk. It does not count as a second active implementation when deciding whether a capability belongs in the shared commercial core.

## 2. Executive findings

1. Gestudio is the only current repository with a complete commercial and financial chain: obligations, payment allocations, credit, cash ledger, receipts and stock movements.
2. The current inventarios-muebleria backend contains authentication and `ThirdParty`. Products, quotes, sales, installments, payments, cash, documents and inventory were removed at `831a05d` and were not restored by `b692017`.
3. The inventarios frontend still exposes cash and quote workflows against backend capabilities that no longer exist. It therefore cannot be treated as evidence of a working end-to-end commercial slice.
4. `Alumno` and `ThirdParty` overlap in contact data, but model different aggregates. Combining them would import academy guardianship and commercial profiling into a generic entity.
5. No shared JPA entity is justified by the evidence. Shared code should start with identifiers, value objects, application contracts and ports.
6. The first safe extractable slice is a tenant-scoped **party reference contract**, not a universal party profile.
7. Receivables, payments, cash and inventory remain candidate shared capabilities, but implementation must wait for a second current use that satisfies the minimum invariants defined here.
8. Gestudio's financial guarantees are a minimum acceptance floor for future implementations, not a schema to copy blindly.

## 3. Repository reality check

### 3.1 Gestudio current state

Relevant current evidence includes:

- `backend/src/main/java/gestudio/entidades/Alumno.java`
- `backend/src/main/java/gestudio/entidades/Cargo.java`
- `backend/src/main/java/gestudio/entidades/Pago.java`
- `backend/src/main/java/gestudio/entidades/AplicacionPago.java`
- `backend/src/main/java/gestudio/entidades/MovimientoCredito.java`
- `backend/src/main/java/gestudio/entidades/MovimientoCaja.java`
- `backend/src/main/java/gestudio/entidades/MovimientoStock.java`
- `backend/src/main/java/gestudio/servicios/pago/PagoServicio.java`
- `backend/src/main/java/gestudio/servicios/caja/CajaServicio.java`
- `backend/src/main/java/gestudio/servicios/stock/StockServicio.java`
- `backend/src/main/java/gestudio/tarifas/persistence/TarifaDisciplina.java`
- `docs/refactor/03-business-invariants.md`
- `docs/refactor/11-canonical-domain-model.md`

The frozen product explicitly documents and implements:

- exact decimal money;
- immutable original obligations;
- explicit payment allocations;
- explicit overpayment-to-credit consent;
- stable financial lock ordering;
- idempotent payment and cancellation requests;
- append-only compensating movements;
- PostgreSQL-computed cash totals;
- receipt jobs outside the financial transaction;
- recoverable claims and leases;
- no physical deletion of financial or stock history.

### 3.2 inventarios-muebleria current state

Current evidence includes:

- `backend/src/main/java/com/scalaris/parties/domain/ThirdParty.java`
- `backend/src/main/java/com/scalaris/parties/service/ThirdPartyService.java`
- `backend/src/main/java/com/scalaris/parties/repo/ThirdPartyRepository.java`
- `backend/src/main/resources/db/migration/V1__baseline_auth.sql`
- `backend/src/main/java/com/scalaris/config/SecurityBeansConfig.java`

The current backend supports:

- user authentication with global roles;
- customer, supplier or combined third parties;
- person/company classification;
- addresses and tax identifiers;
- active/inactive lifecycle;
- active-record email/document uniqueness;
- search and soft deletion.

It does not contain current backend implementations for:

- products;
- quotes;
- sales;
- receivables/installments;
- payments or allocations;
- cash registers or sessions;
- credit balances;
- documents;
- stock events.

### 3.3 inventarios-muebleria frontend drift

The frozen frontend still contains pages such as:

- `frontend/src/pages/CajaPage.tsx`
- `frontend/src/pages/PresupuestosPage.tsx`
- `frontend/src/pages/InventarioPage.tsx`
- `frontend/src/pages/ClientesPage.tsx`

`CajaPage` explicitly notes that recent movements and cancellation are not exposed by its context and that an audit user header is not sent. `PresupuestosPage` still creates quotes and requests sale conversion. Those flows no longer have corresponding backend modules in the current frozen commit.

This is a release-blocking product inconsistency, not evidence of a current working capability.

## 4. Capability classification matrix

| Capability | Gestudio current | inventarios current | inventarios legacy | Classification | Platform decision |
|---|---|---|---|---|---|
| People and organizations | `Alumno`, academy-specific | `ThirdParty`, customer/supplier profile | `ClienteProveedor` | Shared reference need; incompatible profiles | Shared `PartyRef` contract only. Profiles stay vertical. |
| Products and services | disciplines and stock products | absent | `Producto`, category, stock fields | One current use plus legacy evidence | Defer shared catalog implementation. |
| Pricing | effective-dated discipline tariff | absent | product suggested price and quote item price | Different pricing models | Vertical. Share money/value primitives only. |
| Quotes | no generic quote aggregate | absent | `Presupuesto` | Single legacy use | Mueblería vertical. |
| Obligations / receivables | mature `Cargo` | absent | sale balance and installment `Cuota` | Strong conceptual overlap, one current use | Candidate contract; defer implementation. |
| Payments | mature `Pago` | absent | payment tied to sale | One current use plus weak legacy use | Candidate; defer shared persistence. |
| Payment allocations | `AplicacionPago` to `Cargo` | absent | `PagoAplicacionCuota` | Similar relationship, different guarantees | Candidate contract; Gestudio invariants are minimum floor. |
| Cash | event ledger, no register session | absent | register, mutable balance and session | Incompatible operational models | Vertical aggregates; optional shared movement contract later. |
| Credit balance | append-only credit movements | absent | absent | Single use | Gestudio vertical. |
| Receipts/documents | receipt plus recoverable pending worker | absent | attachment/PDF abstractions | Different maturity and purpose | Shared outbox only; documents remain vertical. |
| Inventory | current stock movement ledger | absent | product stock and stock movements | One current use plus legacy evidence | Candidate after a second current implementation. |

## 5. People, organizations, customers and suppliers

### 5.1 Gestudio vocabulary and invariants

`Alumno` is a student lifecycle aggregate, not a generic customer:

- name, surname, birth date and contact fields;
- incorporation and withdrawal dates;
- guardian names;
- authorization to leave alone;
- academy notes;
- active flag and optimistic version.

The guardian and safety fields are operational academy data. They must not enter a shared commercial party schema.

### 5.2 inventarios current vocabulary and invariants

`ThirdParty` represents a commercial party:

- `CUSTOMER`, `SUPPLIER` or `BOTH`;
- `PERSON` or `COMPANY`;
- display and legal names;
- email, phone and document;
- fiscal and company structure fields;
- addresses and tax identifiers;
- commercial profiling such as style preference, homes, partner and employee count;
- active/inactive lifecycle.

The service replaces address and tax-ID collections during update. The current database uses global uniqueness for email, document and tax identifiers. Those constraints are not tenant-ready and would incorrectly collide across platform tenants.

### 5.3 Shared classification

Shared:

- tenant ownership;
- opaque party identifier;
- display-name snapshot;
- active/inactive state;
- basic contact lookup;
- source-system mapping;
- historical reference semantics.

Vertical:

- enrollment and withdrawal lifecycle;
- guardian and child-safety data;
- customer/supplier classification;
- fiscal profile;
- commercial demographic/preferences;
- addresses and tax identifiers as authoritative records.

### 5.4 Canonical contract

The first shared contract should be intentionally narrow:

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

`PartyRef` is a stable commercial reference. It is not a shared profile entity and does not own academy or customer-specific fields.

Historical documents and financial rows store the display snapshot used at issuance. They do not reinterpret the current profile when rendered later.

## 6. Products, services and catalog

### 6.1 Gestudio

Gestudio has two distinct concepts:

- discipline/service configuration with effective-dated tariffs;
- physical stock products with stock movements and historical sale pricing.

These concepts must not be collapsed into a single generic item because service enrollment and physical inventory have different invariants.

### 6.2 inventarios legacy

The legacy `Producto` contained:

- global SKU;
- category;
- unit of measure;
- cost and suggested sale price;
- actual, committed and minimum stock;
- active state and description.

The old API supported stock adjustment and stock movement/alert reads.

### 6.3 Decision

There is insufficient evidence for a shared catalog aggregate today. A future shared catalog may expose a `CatalogItemRef` and pricing snapshots, but inventory ownership, service scheduling and stock commitment remain separate capabilities.

Required second-use evidence before extraction:

- current product lifecycle;
- tenant-scoped SKU semantics;
- immutable stock ledger;
- reservation/commitment semantics;
- reconciliation between stored balance and events;
- tested cancellation/reversal behavior.

## 7. Pricing and quotes

### 7.1 Gestudio tariff model

`TarifaDisciplina` is effective-dated and records:

- discipline;
- start of validity;
- monthly fee;
- enrollment fee;
- single class and trial class prices;
- reason, author and timestamp.

This is a price policy over time, not a customer quote.

### 7.2 inventarios legacy quote model

`Presupuesto` contained:

- customer and seller;
- state lifecycle;
- issue and expiry dates;
- delivery and payment terms;
- description;
- subtotal, discount, fiscal amount and total;
- item rows;
- PDF and design references.

The frozen frontend computes an estimated total with JavaScript numbers before sending the request. Server-side recalculation remains mandatory for any future implementation.

### 7.3 Decision

Quotes remain a mueblería vertical capability. Effective-dated tariffs remain an academy vertical capability. The only immediately shareable components are exact `Money`, quantity and immutable issued-price snapshots.

## 8. Obligations and accounts receivable

### 8.1 Gestudio `Cargo`

`Cargo` is an explicit original obligation:

- immutable type, description, original amount and issue date;
- due date and status;
- exactly one business origin such as monthly fee, enrollment, concept or stock sale;
- optional source charge for surcharge derivation;
- idempotency key and optimistic version.

Balance is derived from the original amount minus active payment allocations and applied credit consumption. The original amount is never rewritten to represent the remaining balance.

### 8.2 inventarios legacy sale/installment model

The legacy model stored:

- `Venta.total` and mutable `saldoPendiente`;
- optional payment plan;
- installment rows with amount, balance, due date and state;
- payment allocation to installments.

This resembles receivables, but it lacks the demonstrated event and compensation guarantees of Gestudio.

### 8.3 Candidate canonical contract

A future shared receivable contract should use:

```text
ReceivableObligation
  tenantId
  obligationId
  partyRef
  sourceRef
  issuedAt
  dueAt
  originalAmount
  currency
  status

Allocation
  paymentId
  obligationId
  amount
  state

Balance = originalAmount - active allocations - active credit consumption
```

No current shared entity is approved. The contract becomes implementable only after a second current vertical proves the parity matrix in section 15.

## 9. Payments and allocations

### 9.1 Gestudio

Gestudio's payment flow proves:

- functional permission enforcement;
- operation-level idempotency lock;
- request-hash conflict detection;
- active student lock;
- deterministic cargo lock ordering;
- duplicate cargo rejection;
- no overapplication;
- total allocations not exceeding money received;
- explicit consent before generating credit from excess;
- cash movement creation;
- receipt and pending document job creation;
- idempotent cancellation with compensating allocation, cash and credit rows.

### 9.2 inventarios legacy

The legacy payment stored:

- sale;
- payment medium;
- cash register;
- payment time;
- gross amount;
- bank/processor discount;
- net amount;
- processor and external reference.

`PagoAplicacionCuota` linked a payment to an installment and amount. The frozen legacy entities do not expose payment status, reversal references, idempotency keys or request hashes.

### 9.3 Differences that must remain explicit

- Gestudio receives one amount and optionally allocates it to several obligations.
- Legacy mueblería ties the payment directly to one sale and optionally installments.
- Gestudio models overpayment as explicit credit.
- Legacy mueblería models processor discount as gross/net payment differences.
- Gestudio cancellation is append-only and tested.
- Legacy mueblería cancellation guarantees are not proven.

### 9.4 Decision

The relationship `Payment -> Allocation -> Obligation` is a strong future shared candidate. It is not ready for implementation from the current evidence because the second use is legacy-only.

## 10. Cash sessions and movements

### 10.1 Gestudio cash model

Gestudio models cash as an event ledger linked to payment methods, payments or expenses. Its summary queries PostgreSQL totals for:

- income;
- expenses;
- positive/negative adjustments;
- reversal income/expense;
- net result.

It has no explicit cash-register aggregate or opening/closing session in the canonical model.

### 10.2 inventarios legacy cash model

The legacy model had:

- multiple cash registers by type;
- mutable stored balance;
- negative-balance policy;
- opening sessions with initial balance;
- mandatory open session for movements;
- income, expense and adjustment categories;
- sale and user references;
- cancellation through an inverse movement linked to the original.

### 10.3 Decision

These are materially different operating models. Do not put `CashRegister`, `CashSession` or mutable balance in the shared kernel.

A future shared contract may standardize only:

- exact amount;
- direction;
- occurrence time;
- source reference;
- original/reversal relationship;
- idempotency and audit context.

Register sessions and closing procedures remain vertical policies.

## 11. Credit balances

Only Gestudio has a current, explicit credit ledger:

- generation;
- consumption;
- adjustment;
- reversal;
- payment/cargo references;
- idempotency and request hash.

Credit remains an academy vertical capability until another current product proves the same need. It must not be inferred from an installment balance or a negative receivable.

## 12. Receipts and documents

### 12.1 Gestudio

A payment creates:

- one receipt record;
- one pending receipt effect with state, attempts, next execution, claim and lease.

PDF/filesystem/email execution happens after financial commit. A failed document operation cannot roll back or duplicate the payment.

### 12.2 inventarios legacy

The legacy model contained generic attachment and PDF gateway abstractions, and quote send workflows. Evidence for durable retry, idempotent delivery and lease recovery was not found in the frozen legacy snapshot.

### 12.3 Decision

The platform transactional outbox already provides the shared reliability mechanism. Receipt, quote and invoice document models remain vertical because their legal content, numbering and lifecycle differ.

## 13. Inventory ownership and stock events

### 13.1 Gestudio

Gestudio uses an append-only stock movement model. Stock sale preserves historical unit price and reversal creates a compensating event.

### 13.2 inventarios legacy

The legacy product stored actual, committed and minimum stock and exposed stock adjustments, movement history and alerts. Its current backend no longer contains these modules.

### 13.3 Decision

Inventory is a future shared candidate only after a current mueblería vertical is restored with:

- tenant ownership;
- event-derived or reconciled balance;
- stable units and scale;
- source references;
- reservations/commitments;
- append-only reversal;
- concurrency and idempotency tests.

## 14. Authorization, tenancy and operational readiness

### 14.1 Gestudio

Gestudio has functional permission codes and deny-by-default HTTP behavior, but its domain records are not platform tenant-owned.

### 14.2 inventarios current

The current backend authenticates requests and stores one global role on the user. Every non-public endpoint merely requires authentication. Third-party and auth tables have no tenant ownership.

### 14.3 Platform implication

Neither source persistence model should be copied. New platform commercial data must use:

- `TenantContext` on every operation;
- tenant-scoped identifiers and unique constraints;
- membership-scoped permissions;
- module entitlement checks;
- reliable command boundary for financial mutations;
- audit, idempotency and outbox from the platform kernel.

## 15. First extractable slice: Party Reference Directory

### 15.1 Scope

Implement a tenant-scoped party-reference capability in the shared commercial core. It maps vertical records without moving or merging profile data.

In scope:

- `PartyId` and `PartyRef` value objects;
- tenant/source/source-id mapping;
- active-state resolution;
- display-name snapshot;
- lookup and search port;
- adapters for Gestudio students and Scalaris third parties;
- audit and idempotent mapping import.

Out of scope:

- guardians;
- addresses;
- tax IDs;
- fiscal status;
- customer/supplier behavior;
- credit limits;
- account balances;
- shared profile editing;
- shared JPA inheritance.

### 15.2 Acceptance and parity matrix

| Scenario | Expected result |
|---|---|
| Same source identifier in two tenants | Allowed; mappings are tenant-scoped. |
| Same tenant/source/source-id imported twice | One mapping and deterministic replay. |
| Same key with changed source/display data | Conflict or explicit reconciliation command; never silent remap. |
| Active student mapped | Resolves to active `PartyRef`. |
| Active third party mapped | Resolves to active `PartyRef`. |
| Inactive source record used for a new transaction | Rejected. |
| Existing historical transaction references a now-inactive party | Readable through stored `PartyRef` and display snapshot. |
| Source display name changes | New operations use the current name; historical issued records keep their snapshot. |
| Request uses another tenant's party ID | `403` or not-found according to the endpoint contract; no data leak. |
| Adapter unavailable | Explicit operational failure; no fabricated party data. |
| Search result | Contains only minimal reference data, never vertical private fields. |

### 15.3 Measurable parity criteria

Before declaring the slice complete:

- 100% of active Gestudio students selected for migration have a deterministic mapping;
- 100% of active Scalaris third parties selected for migration have a deterministic mapping;
- duplicate and missing source IDs are reported before writes;
- no academy guardian/safety field appears in the shared schema or API;
- no Scalaris demographic/fiscal field appears in the shared schema or API;
- PostgreSQL tests prove tenant isolation and import idempotency;
- adapters produce the same `PartyRef` contract;
- historical display snapshots remain unchanged after profile updates.

## 16. Migration and reconciliation plan

### Phase 0 — freeze and inventory

- retain the commit hashes from section 1;
- record source row counts and active/inactive counts;
- detect missing and duplicate source IDs;
- detect duplicate documents/emails as data-quality findings, not merge instructions.

### Phase 1 — mapping-only schema

Create platform mappings without copying vertical profiles:

```text
party_reference
  tenant_id
  id
  source_type
  source_id
  active
  current_display_name
  created_at
  updated_at

unique (tenant_id, source_type, source_id)
```

Financial/document records use `party_reference.id` and their own issued display snapshots.

### Phase 2 — dry-run adapters

For each source:

- load source identity and active state;
- normalize only display whitespace;
- produce deterministic proposed mappings;
- generate reconciliation reports;
- write nothing when unresolved duplicates or missing identifiers exist.

### Phase 3 — idempotent import

Use the platform reliable command boundary:

- key scope: tenant + source type + source ID;
- request hash includes active state and current display name;
- audit each mapping result;
- publish outbox events only after commit.

### Phase 4 — dual-read verification

- compare adapter source lookup against platform mapping;
- sample active, inactive and renamed records;
- block commercial cutover until discrepancy count is zero or explicitly waived.

### Phase 5 — controlled adoption

New platform commercial slices reference `PartyRef`. Source products retain ownership of profile editing until a separate profile migration is designed.

## 17. Future receivable/payment slice gate

A shared receivable/payment implementation may begin only when a second current vertical demonstrates all of the following:

- immutable original obligation amount;
- exact decimal and currency rules;
- explicit payment allocation rows;
- deterministic no-overapplication rule;
- explicit overpayment policy;
- stable lock ordering;
- request idempotency and hash conflict;
- append-only cancellation/reversal;
- audit and transactional outbox;
- tenant isolation;
- PostgreSQL concurrency tests;
- reconciliation from source balances to allocations.

Gestudio already satisfies most of this gate. The legacy mueblería model does not, and the current mueblería backend does not contain the capability.

## 18. Known defects and migration risks

### Critical

1. **Current mueblería backend/frontend mismatch**: frontend cash, quote and inventory workflows target removed backend modules.
2. **No tenant ownership in either source model**: all migrated data requires explicit tenant assignment.
3. **Global uniqueness in current Scalaris parties**: email/document/tax-ID indexes must not become platform-global constraints.
4. **Legacy mutable financial balances**: `Venta.saldoPendiente`, `Cuota.saldo` and `Caja.saldo` require reconciliation against source events before migration.

### High

5. Legacy payment has no demonstrated idempotency or reversal fields.
6. Legacy quote totals may be precomputed by the frontend; canonical totals must be server-authoritative.
7. Current `ThirdParty` collection replacement can physically remove address/tax-ID children; issued documents must use snapshots.
8. Source IDs use mixed `Long` and UUID strategies; platform mapping must never reuse them as platform IDs.
9. Money precision differs (`19,2` versus `18,2`) and quantities use separate scales.
10. The current Scalaris role model is global and cannot authorize tenant commercial operations.

### Medium

11. Gestudio `Alumno` has one full-name interpretation while Scalaris has display/legal names; display construction must be adapter-owned.
12. Legacy stock keeps stored balances and alerts; event reconciliation is not proven.
13. Generic document abstractions do not prove legal numbering or durable delivery.
14. Frontend pages use JavaScript `number`; server contracts must use decimal strings for authoritative money.

## 19. Rejected shortcuts

### One universal `Party` JPA entity

Rejected because it would mix guardianship, child safety, fiscal data, supplier status and commercial demographics.

### Copy Gestudio `Cargo` as the platform receivable schema immediately

Rejected because Gestudio is only one current proven implementation. Its invariants are valuable, but its source references and academy vocabulary are not automatically universal.

### Restore the deleted mueblería backend into the shared core

Rejected because the deleted code has weaker financial guarantees, no tenant model and no current end-to-end parity.

### Treat the surviving frontend as proof of capability

Rejected because a UI calling absent endpoints is evidence of drift, not functioning behavior.

### Share cash-register sessions globally

Rejected because Gestudio and legacy mueblería use incompatible cash operating models.

### Migrate profile data before references are stable

Rejected because it expands scope and couples every future commercial slice to unresolved profile semantics.

## 20. Final recommendation

1. Implement the Party Reference Directory as the next shared commercial slice.
2. Keep Gestudio academy profiles and Scalaris third-party profiles vertical.
3. Open a separate product-repair stream for inventarios-muebleria backend/frontend drift; do not solve it inside the platform core.
4. Require a second current implementation before extracting receivables, payments, cash or inventory persistence.
5. Use Gestudio's financial invariants as the minimum gate for that second implementation.
6. Continue using shared platform tenancy, RBAC, audit, idempotency and outbox rather than migrating source infrastructure.
