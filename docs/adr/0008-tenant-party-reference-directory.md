# ADR 0008 — Tenant-scoped Party Reference Directory

- Status: accepted
- Date: 2026-07-16

## Context

Gestudio students and Scalaris customer/supplier records both need to participate in future commercial operations. Their overlapping contact fields do not make them the same aggregate:

- Gestudio owns academy enrollment, guardianship and child-safety data.
- Scalaris owns customer/supplier, person/company, tax and commercial-profile data.

The commercial capability comparison in ADR 0007 approved one narrow shared concept: a stable tenant-owned reference to a vertical party record.

## Decision

1. `commercial-core` owns `PartyId`, `PartyRef` and the Party Reference Directory.
2. Source products keep ownership of profile persistence and editing.
3. A mapping is uniquely identified by `(tenant, sourceType, sourceId)`.
4. Approved source types are canonical and contract-tested across JSON, Java and TypeScript.
5. Initial source types are `GESTUDIO_STUDENT` and `SCALARIS_THIRD_PARTY`.
6. Unknown source types are rejected. They are never converted into a generic fallback.
7. Shared persistence contains only:
   - platform ID;
   - tenant ID;
   - source type and source identifier;
   - current display name;
   - active/inactive status;
   - technical timestamps and version.
8. Email, document, tax ID, address, guardianship, customer/supplier role and profile attributes are forbidden from the shared table and API.
9. New commercial operations require an active party and store a `PartyRef` display-name snapshot.
10. Renaming a source record updates the current directory name but does not rewrite snapshots already issued into financial or document records.
11. Deactivation prevents new selections but does not make historical references unreadable.
12. Imports execute through the platform reliable command boundary.
13. The reliable request hash includes source type, source ID, display name and lifecycle status.
14. Reusing an idempotency key with different content returns deterministic `409`.
15. A PostgreSQL advisory transaction lock serializes each source key even when callers use different idempotency keys.
16. Mapping creation/update, audit and outbox commit atomically.
17. Unchanged imports audit the command but do not publish a change event.
18. Reconciliation is a read-only dry run. Invalid, duplicate and unknown source records are reported before any write.
19. Source adapters implement a narrow port and return only `PartySourceRecord`.
20. The platform does not connect directly to product repositories as part of this slice; real adapters belong to later migration/integration work.

## API semantics

- `commercial.parties.read` permits tenant-scoped lookup and search.
- `commercial.parties.manage` permits import and reconciliation.
- anonymous access returns `401`;
- authenticated calls without the permission return `403`;
- missing or cross-tenant references return `404` without revealing ownership;
- inactive selection returns `409 party_reference_inactive`;
- changed-content idempotency reuse returns `409 idempotency_key_conflict`.

## Events

Changes publish one of:

- `commercial.party-reference.created`
- `commercial.party-reference.updated`

The event payload contains only reference fields. No vertical profile payload is copied into the outbox.

## Consequences

- Future receivable, quote and document slices can reference parties consistently.
- Vertical profiles may evolve independently.
- Historical output is stable across profile renames.
- Search is intentionally limited and cannot replace a source product's complete profile search.
- New source systems require a deliberate contract and migration rather than arbitrary strings.
- A separate migration project is still required to implement production Gestudio and Scalaris adapters.

## Rejected alternatives

### Universal shared party profile

Rejected because it becomes a nullable union of student, guardian, customer, supplier, person and company data.

### Copy all source fields into JSON

Rejected because an untyped payload would bypass the boundary while still duplicating sensitive vertical data.

### Use source IDs as platform IDs

Rejected because source systems use different identifier types and the same value may exist in multiple tenants or sources.

### Allow arbitrary source type strings

Rejected because typos and undocumented integrations would become permanent persistence contracts.

### Rewrite historical names after profile updates

Rejected because issued financial and document records must preserve what was presented at issuance.

### Publish an outbox event for unchanged imports

Rejected because it produces false change signals and unnecessary downstream work.
