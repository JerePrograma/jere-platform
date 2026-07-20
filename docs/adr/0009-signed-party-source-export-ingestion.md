# ADR 0009 — Signed party-source export ingestion

- Status: accepted
- Date: 2026-07-20
- Tracking: #51, #56

## Context

ADR 0008 deliberately kept student and commercial-party profiles in their source
products. Jere Platform still needs a production-safe way to refresh the narrow
Party Reference Directory without reading a source database, copying a source
entity or accepting unauthenticated files.

The source repositories have no compatible platform export endpoint at their
frozen commits. Implementing their emitters is separate, coordinated work in those
repositories. This decision defines the platform-owned receiving boundary first.

## Decision

1. A source product emits a versioned JSON artifact containing only tenant ID,
   source type, checkpoint, optional next cursor, full-snapshot flag and reference
   records (`sourceId`, `displayName`, `active`).
2. The platform publishes the canonical v1 JSON Schema and anonymized fixtures in
   `contracts/parties/`.
3. The exact request bytes are authenticated with HMAC-SHA256 before JSON parsing.
4. Gestudio and Scalaris have separate secrets. Each source accepts a current and
   previous secret so rotation does not require downtime.
5. Configured secrets must contain at least 32 UTF-8 bytes and stay outside Git.
6. The authenticated platform tenant must equal the artifact tenant. A valid
   artifact for another tenant is rejected.
7. Unknown source types, versions, fields and malformed signatures are rejected.
8. Requests are limited to 1 MB and 1,000 records. Empty pages and empty complete
   snapshots are valid.
9. `commercial.parties.manage` is required for reconciliation and import.
10. Reconciliation is read-only. Any invalid or duplicate record blocks the batch.
11. A complete snapshot reports mapped source IDs that are absent. Absence blocks
    import and never deletes or deactivates a party reference automatically.
12. Accepted batches execute through one reliable command. The tenant, source,
    checkpoint and next cursor identify a page; a changed retry returns `409`.
13. Page records are sorted before acquiring source-key locks. Mapping changes,
    audit rows, outbox events and the idempotent response commit atomically.
14. The existing idempotency ledger retains accepted export responses for 365 days;
    no separate checkpoint table is introduced for this increment.
15. Metrics count read, rejected, imported, unchanged, replayed and conflicted
    records by approved source type. Secrets and record payloads are never metric
    tags or log fields.

## Consequences

- The platform-side contract is testable before source emitters exist.
- A source integration can resume by resending the same page or following
  `nextCursor`; exact retries replay safely.
- Secret rotation supports an overlap window, but secret distribution remains an
  operational responsibility outside this repository.
- Complete-snapshot drift requires an operator decision instead of destructive
  synchronization.
- Issue #51 remains incomplete until source-owned emitters produce this contract.
- No Flyway migration or new dependency is required.

## Recovery

- Replay the same signed artifact after a transient failure.
- Keep the old secret in the previous-secret slot while producers adopt the new
  current secret, then remove it after the agreed overlap window.
- Correct a bad export with a new checkpoint. Reusing a checkpoint/page identity
  with changed content is intentionally rejected.
- Recover an incorrect accepted mapping through a later corrective export; do not
  delete history or mutate the idempotency ledger.

## Rejected alternatives

### Direct source-database access

Rejected because it couples deployments, bypasses source authorization and leaks
profile schema into the platform.

### Generic webhook payload

Rejected because arbitrary fields and source strings would defeat ADR 0008.

### Broker or object-storage service

Rejected because current volume and operations do not justify new infrastructure.

### Delete records absent from a full snapshot

Rejected because absence can represent a partial or faulty export and destructive
history mutation is not an acceptable reconciliation policy.
