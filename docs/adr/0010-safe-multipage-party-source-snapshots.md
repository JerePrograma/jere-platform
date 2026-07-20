# ADR 0010 — Safe multipage party-source snapshots

- Status: accepted
- Date: 2026-07-20
- Tracking: #51, #59

## Context

Contract v1 originally allowed a producer to send multiple pages by carrying a
`nextCursor`, and required the final page of a full snapshot to use
`fullSnapshot: true` with `nextCursor: null`. The receiver reconciled absences
only against records in the current request. Consequently, a final page could
incorrectly report every reference from earlier pages as absent. The payload also
lacked a page ordinal or declared page count, so the receiver could not prove that
all pages had arrived before accepting completion.

This is a contract contradiction, not an emitter-specific implementation defect.
Issue #59 records it separately from the Gestudio emitter.

## Decision

1. Contract version remains `1` and adds optional `pageNumber` and `pageCount`
   integers. They must appear together and each is limited to 1..1,000.
2. A producer using page metadata must send pages in strict ordinal order.
   `pageNumber == pageCount`, `fullSnapshot: true` and `nextCursor: null` are
   equivalent final-page conditions.
3. Artifacts without page metadata retain the accepted v1 behavior for backward
   compatibility. They do not gain cross-page completeness proof.
4. The receiver persists snapshot headers, accepted page SHA-256 hashes and the
   minimal set of source IDs per page. It never persists raw export payloads.
5. The identity is tenant + approved source type + checkpoint. A PostgreSQL
   transaction advisory lock serializes progress for that identity.
6. A retry of an accepted page must carry the exact same raw-byte hash. Changed
   content returns `party_source_page_conflict`.
7. A page cannot repeat a source ID already accepted for the same checkpoint.
8. The final page is rejected until every prior ordinal is committed. Final
   reconciliation uses the union of source IDs from all accepted pages plus the
   current page.
9. Snapshot progress, party-reference mutations, audit, outbox and the reliable
   command response commit atomically in the existing transaction boundary.
10. Missing, incomplete, out-of-order and conflicting progress returns a
    deterministic `409`; no party reference is partially written.
11. Progress retention cleanup is deliberately not automated in this increment.
    Operators must size and monitor the tables before production deployment.

## Consequences

- Full-snapshot absence findings are correct for multi-page exports.
- Missing pages are detectable and cannot be disguised as a complete snapshot.
- Legacy v1 fixtures and single-page producers remain compatible.
- Flyway V8 owns three `commercial-core` progress tables.
- The progress store contains external source identifiers, but no names, payloads,
  signatures or secrets.
- Checkpoints are immutable receiver identities; corrected exports require a new
  checkpoint.

## Recovery

- Retry the exact signed page after a transient error.
- Resume with the next ordinal only after the prior page is accepted.
- Use a new checkpoint after a content conflict or source correction.
- Do not edit accepted progress or Flyway V8. A future retention policy must be a
  separately reviewed forward change.

## Rejected alternatives

### Treat the final request as the complete source set

Rejected because it creates false absences for every earlier page.

### Store only a page counter

Rejected because final reconciliation also needs the complete source-ID set and
must reject duplicates across page boundaries.

### Replace v1 with v2

Rejected because the additive metadata can be validated unambiguously while
preserving existing v1 producers.
