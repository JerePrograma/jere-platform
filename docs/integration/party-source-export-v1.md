# Party-source export v1

This is the platform-owned ingestion contract for issue #56. It imports only
reference fields approved by ADR 0008; source products remain owners of student,
customer, supplier, contact, tax, guardian and address data.

## Contract artifacts

- Schema: `contracts/parties/source-export-v1.schema.json`
- Gestudio fixture: `contracts/parties/fixtures/gestudio-students-v1.json`
- Scalaris fixture: `contracts/parties/fixtures/scalaris-third-parties-v1.json`

The exact JSON bytes sent over HTTP must be the bytes used to calculate the
signature. Producers must not parse and reserialize between signing and sending.

## Authentication and authorization

Configure at least the current secret for each enabled source:

| Source | Current | Previous rotation slot |
|---|---|---|
| Gestudio | `PARTY_SOURCE_GESTUDIO_CURRENT_SECRET` | `PARTY_SOURCE_GESTUDIO_PREVIOUS_SECRET` |
| Scalaris | `PARTY_SOURCE_SCALARIS_CURRENT_SECRET` | `PARTY_SOURCE_SCALARIS_PREVIOUS_SECRET` |

Every configured secret must have at least 32 UTF-8 bytes. Secrets must be supplied
by the runtime secret manager and must not be committed or printed.

The caller also needs a platform access token for the artifact tenant and the
`commercial.parties.manage` permission. The platform rejects a tenant mismatch even
when the HMAC is valid.

## Requests

Send the raw JSON body with:

```text
Content-Type: application/json
Authorization: Bearer <platform access token>
X-Party-Source-Type: GESTUDIO_STUDENT | SCALARIS_THIRD_PARTY
X-Party-Export-Signature: sha256=<lowercase or uppercase 64-character hex HMAC>
```

Endpoints:

```text
POST /api/party-source-exports/reconciliation
POST /api/party-source-exports/imports
```

`reconciliation` never writes. `imports` first runs the same reconciliation and
returns `422` without writes when any finding is present.

## Paging and retry semantics

- `checkpoint` identifies the source snapshot or extraction run.
- `nextCursor` identifies the continuation after the current page.
- The reliable page identity is tenant + source type + checkpoint + next cursor.
- Sending the same page again returns the stored response with `replayed: true`.
- Sending different content for the same page identity returns `409`.
- A complete snapshot must have `nextCursor: null`.
- Empty pages are valid. An empty complete snapshot reports every existing mapping
  of that source as absent and does not delete it.

Producers should keep a checkpoint stable during one extraction, persist the next
cursor they have successfully delivered and use a new checkpoint for a corrected
export.

## Limits and outcomes

- Maximum raw body: 1,000,000 bytes.
- Maximum records per artifact: 1,000.
- Unknown JSON fields and contract versions are rejected.
- Supported reference state is active/inactive; omitted or null `active` is a
  blocking reconciliation finding.
- All accepted page changes, audit data and outbox events commit atomically.

| HTTP | Code or meaning |
|---:|---|
| 200 | Reconciliation completed or batch accepted/replayed |
| 400 | `invalid_party_source_export` |
| 401 | Missing platform authentication or `party_source_authentication_failed` |
| 403 | Missing permission or `party_source_tenant_mismatch` |
| 409 | Idempotency content conflict or in-progress request |
| 422 | Reconciliation findings; no write occurred |
| 503 | `party_source_not_configured` |

## Operational evidence

Micrometer counter `jere.party_source_export.records` uses the tags `source` and
`outcome`. Valid outcomes are `read`, `rejected`, `imported`, `unchanged`,
`replayed` and `conflicted`.

Alerting and dashboards are deployment concerns not yet present in this repository.
Operators can safely retry exact artifacts. They must investigate reconciliation
findings before creating a corrected artifact with a new checkpoint.
