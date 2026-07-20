# Domain Map

## Boundary rules

1. `kernel` owns shared technical and SaaS access boundaries and depends on no business module.
2. `commercial-core` may depend on public kernel contracts and never on verticals.
3. `verticals` may depend on public kernel and commercial contracts.
4. `platform-api` is the inbound composition layer; it does not become a business owner.
5. A module owns its tables. Cross-module reads use public application interfaces or deliberate projections.
6. Tenant context is resolved from an authenticated session and active membership, not trusted from a request body.

## Current bounded contexts

| Context | State | Owner | Owned data | Commands | Queries | Events | Dependencies |
|---|---|---|---|---|---|---|---|
| Tenancy and Organizations | IMPLEMENTED | `kernel.tenancy` | tenant, organization, branch, membership, membership-branch | bootstrap/provision tenant membership; grant branch access | resolve tenant access; list active branches | None published | None |
| Identity and Sessions | IMPLEMENTED | `kernel.identity` | credential, session family, refresh token, bootstrap marker | register credentials; login; rotate refresh; logout/revoke | validate access session; resolve session context | Security outcomes are audited, not externally published | Tenancy public contracts |
| Authorization | IMPLEMENTED | `kernel.authorization` | permission catalog, role templates, tenant roles, assignments, entitlements, feature flags | reconcile managed roles; assign roles; change entitlements | effective authorization snapshot | None published | Tenancy public contracts |
| Reliability and Audit | IMPLEMENTED | `kernel.reliability` | audit event, idempotency record, outbox event | execute reliable command; claim/complete/requeue outbox; cleanup eligible rows | audit history; reliability summary; dead events | Stores events drafted by owning use cases | Tenancy identity references |
| Party References | IMPLEMENTED | `commercial.parties` | tenant party reference | import mapping; update current name/status; dry-run reconciliation | require active; find; search | `commercial.party-reference.created`, `commercial.party-reference.updated` | Tenant context; reliable command boundary |
| Source Export Integration | PARTIAL | source products own export; platform owns signed-artifact consumption | Signed v1 artifacts and reliable page replay; source emitters are not implemented | authenticate, reconcile and atomically import approved source records | export findings and accepted/replayed page result | Party-reference events only after accepted changes | Party References; authenticated source contract |
| Platform API | IMPLEMENTED COMPOSITION | `platform-api` | None | HTTP adapters only | HTTP adapters and health | None owned | Public kernel and commercial contracts |
| Product Verticals | PLACEHOLDER | `verticals` | None | None | None | None | Public kernel and commercial contracts |

## Planned contexts, not code commitments

| Context | Intended owner and boundary | Earliest proof required |
|---|---|---|
| CRM | Shared commercial context for contacts, companies and activities; vertical profiles stay outside | One complete tenant-scoped contact flow plus a second reuse case before generic persistence expands |
| Catalog and Pricing | Shared commercial product/service references and price rules | Two current products with compatible invariants |
| Quotes and Sales | Commercial commitments and issued snapshots | Server-authoritative totals, authorization, idempotency and tenant isolation |
| Inventory | Stock ownership, reservations and movements | A current second implementation and PostgreSQL concurrency tests |
| Procurement | Suppliers, purchase orders and receipts | Inventory ownership and party-reference adoption |
| Receivables and Payments | Immutable obligations, allocations and compensating records | Every financial extraction gate in ADR 0007 |
| Treasury | Cash/account movements and reconciliation | Explicit cash model; no assumed universal cash session |
| Documents | Metadata, ownership, retention and storage ports | A real document-producing use case and private-storage decision |
| Notifications | Templates, requests, delivery attempts and preferences | A real provider integration through the outbox |
| Automation and Integrations | Triggers, webhooks and resumable external synchronization | Stable contracts, idempotency and an operational retry owner |
| Reporting | Operational projections and exports | Stable source models and explicit data-retention policy |
| SaaS Billing | Plans, subscriptions, entitlements and usage | Commercial product decision; separate from customer-business billing |
| Academy | Students, guardianship, enrollment, attendance and academy billing | Private-repository decision before sensitive Gestudio logic migrates |
| Appointments | Availability, resources, bookings and reminders | Source inventory and characterization tests |
| Logistics | Quote inputs, distance, loads and tariffs | Source inventory and decision on external distance provider |

Loans, health and taxation remain separate deployments and databases unless a specific regulatory ADR changes that boundary.

## Integration semantics

- Synchronous collaboration uses target-owned immutable contracts or application interfaces.
- Internal events describe completed facts; they are not method calls with delayed syntax.
- External work begins in the transactional outbox. A broker is not required at current scale.
- Every external consumer must tolerate at-least-once delivery.
- Source adapters expose the minimum approved reference fields and cursor metadata. They never expose full vertical profiles to `commercial-core`.
- New source types require a versioned contract change across JSON, Java and TypeScript.

## Service extraction gate

The deployable remains one modular monolith. An owner may propose extraction only when ADR 0001 evidence exists: stable contract and data ownership, independent operational need, observability, deployment/rollback, idempotency and acceptable cost. Logical boundaries precede network boundaries.
