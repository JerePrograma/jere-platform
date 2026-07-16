# ADR 0006 — Audit, idempotency and transactional outbox

- Status: accepted
- Date: 2026-07-16

## Context

Financial operations, external integrations and user-triggered retries require stronger guarantees than a normal request/response transaction. A client may submit the same command more than once, a worker may crash after claiming work, and an external delivery may fail after the originating database transaction has committed.

Sending email, generating documents or invoking external systems inside the originating business transaction would couple database consistency to network availability. Keeping only application logs would not provide tenant-owned, append-only evidence of outcomes.

## Decision

1. Mutating use cases that require retry safety execute through an explicit reliable command boundary.
2. Idempotency ownership is scoped by tenant, operation code and a SHA-256 hash of the client key.
3. The canonical request is hashed independently. Reusing a key with different content is rejected with a deterministic conflict.
4. PostgreSQL row locking and an ownership token serialize equivalent concurrent requests.
5. A completed command stores a typed JSON response that may be replayed without repeating the business effect.
6. The business effect, successful audit event, outbox records and idempotent response commit in one transaction.
7. A rolled-back command records a sanitized failure audit in a separate transaction. Exception messages and complete request payloads are not persisted.
8. Audit rows contain tenant, actor identity, actor membership, action, target, result, correlation and sanitized metadata.
9. Audit history is append-only. PostgreSQL rejects updates and deletes through a trigger.
10. Outbox events are inserted in the originating transaction and dispatched later. No external side effect occurs inside that transaction.
11. Workers claim events using `FOR UPDATE SKIP LOCKED`, a claim token and an expiring lease.
12. An expired claim is recoverable by another worker. A claim alone never removes or acknowledges an event.
13. Delivery failures use bounded exponential backoff. Exhausted events become `DEAD` and remain inspectable.
14. Manual requeue is tenant-scoped and requires an explicit management permission.
15. Operational visibility exposes tenant-scoped counts, dead-letter rows and audit history under independent permissions.
16. Cleanup may delete only expired completed idempotency responses and successfully dispatched outbox events beyond retention.
17. Audit rows, dead-letter events and financial history are never removed by the generic cleanup job.
18. No external broker is introduced initially. PostgreSQL remains the source of truth until throughput or isolation requirements justify another component.

## HTTP semantics

- Anonymous protected calls return `401`.
- Authenticated calls without authority return `403`.
- Reusing an idempotency key with different content returns `409` and `idempotency_key_conflict`.
- An equivalent request still owned by another active execution returns `409` and `idempotency_request_in_progress`.
- Business conflicts remain `409` and are not represented as transport or authentication failures.

## Concurrency and recovery

An idempotency record starts as `IN_PROGRESS` with an owner token and lease. The owning transaction locks the row before executing the command. Once successful, it becomes `COMPLETED` with the serialized response.

An outbox worker claims a bounded batch. Each row moves to `CLAIMED` with a unique token and lease expiry. Completion or failure updates require the same claim token. A process crash leaves the row recoverable after lease expiry.

## Consequences

- Concurrent equivalent commands produce one business effect.
- Client retries can return the original response after a lost network response.
- Outbox publication is atomic with business state but external delivery is eventually consistent.
- External outages do not roll back already-committed business operations.
- Failed deliveries are visible and recoverable instead of disappearing into logs.
- Audit evidence remains available even when the originating transaction rolls back.
- Storage grows intentionally for audit and terminal failures; retention must be operated explicitly.
- Dispatchers must be idempotent because a crash can occur after external delivery but before the outbox row is acknowledged.

## Rejected alternatives

### Send external messages inside the business transaction

Rejected because network latency and external outages would hold database locks and create ambiguous rollback behavior.

### Store plaintext idempotency keys

Rejected because keys may contain sensitive or customer-derived material and do not need to be recoverable.

### Delete an outbox row when claimed

Rejected because a worker crash would permanently lose the event.

### Unlimited retries

Rejected because poison messages would consume capacity forever and hide the need for operational intervention.

### Mutable audit table

Rejected because administrators or application defects could alter the historical evidence the table is meant to preserve.

### Generic exception messages in audit metadata

Rejected because messages can contain credentials, personal data, SQL details or complete payload fragments.

### Add a broker immediately

Rejected because it would add operational complexity before the platform has demonstrated throughput that PostgreSQL outbox processing cannot support.
