# Project Status and Handoff

## Active snapshot

| Field | Value |
|---|---|
| Date | 2026-07-20 |
| Main SHA at mission start | `6a226b9f3024a210bf118beafc83dfea9789f9b0` |
| Main CI at mission start | run `29753154369`, PASS |
| Active branch | `integration/gestudio-student-source-export-v1` |
| Active issues | #51 emitter integration; #59 safe multipage snapshots |
| Active PR | #60, draft |
| Validated implementation head | `bebfe716780a1ea42cc65be6441af9cc5dfe5bae` |
| Published provenance head | `bcaeefa98626ce9369ab4598aa98c6a45f15f0b8` |
| Coordinated source | Gestudio issue #14, branch `feature/signed-student-source-export-v1`, commit `4c88635e76d7814b91e1a8baacf7a9db3a8ca81d` |

This file records verified continuation data. `main` and GitHub remain authoritative if any pending field becomes stale.

## Existing modules

| Module | Capability | State |
|---|---|---|
| `kernel` | tenancy, identity, authorization, audit, idempotency and outbox | IMPLEMENTED |
| `commercial-core` | tenant-scoped Party Reference Directory | IMPLEMENTED |
| `verticals` | module boundary only | PLACEHOLDER |
| `platform-api` | HTTP/security composition and health | IMPLEMENTED |
| `platform-shell` | static foundation page and typed contracts | PARTIAL |
| `ui` | minimal accessible shared primitive | PARTIAL |

## Database and contracts

- Flyway is forward-only through V8 on this branch.
- Tenant-owned uniqueness and composite foreign keys include tenant ownership where applicable.
- Canonical permission contract contains 19 codes.
- Canonical party source types are `GESTUDIO_STUDENT` and `SCALARIS_THIRD_PARTY`.
- Signed export v1 accepts only reference fields, uses reliable page replay and
  proves completeness for producers with explicit page metadata.
- No production customer data, secrets or source database connection is present.

## Current validation

| Gate | Result | Evidence |
|---|---|---|
| Local backend | PASS | Maven verify: 11 unit/contract tests and 46 integration tests; Flyway V1-V8 |
| Local frontend | PASS | permission contract, lint, typecheck, 9 tests and build |
| PowerShell failure propagation | PASS | simulated exit 17 stopped before npm |
| PR #57 CI | PASS | run `29752419502` at head `eff0418` |
| Main post-merge CI | PASS | run `29752682676` at `53972cc` |
| Multipage receiver focal | PASS | 13/13 PostgreSQL integration tests, Flyway V8 |
| Cross-repository smoke | PASS | runtime Gestudio old/new artifacts consumed; sanitized report in `backend/target` |
| Rotation retirement unit | PASS | previous accepted during overlap and rejected after removal |
| Branch full backend/frontend | PASS | Maven verify; npm ci, check and build |

## Durable decisions

- ADR 0001: modular monolith first; services require operational evidence.
- ADR 0002: public proprietary foundation; sensitive vertical logic is blocked.
- ADR 0003: global identity, tenant membership and trusted tenant context.
- ADR 0004: revocable database-validated sessions and rotating refresh tokens.
- ADR 0005: permissions, entitlements, feature flags and configuration are separate.
- ADR 0006: audit, idempotency and PostgreSQL transactional outbox.
- ADR 0007: commercial sharing requires current product evidence.
- ADR 0008: vertical profiles stay owned by source products; only party references are shared.
- ADR 0009: signed, tenant-bound artifacts are reconciled before atomic import.
- ADR 0010: explicit page metadata and persisted source-ID progress prevent false
  absences and incomplete full snapshots.

## Milestone ledger

| Milestone | Issue | PR | Head | CI run | Merge | Capability | State |
|---|---:|---:|---|---:|---|---|---|
| P1.1 | #24 | #38 | `07c0a2c` | `29529473962` | `85c2fbaf` | Tenant membership foundation | COMPLETE |
| P1.2 | #25 | #39 | `152a5e50` | `29531328457` | `2096337a` | Identity and sessions | COMPLETE |
| P1.3 | #26 | #40 | `7908b726` | `29532967291` | `edd92e00` | RBAC and entitlements | COMPLETE |
| P1.4 | #27 | #41 | `88495a37` | `29535099087` | `74ef83ef` | Audit, idempotency and outbox | COMPLETE |
| M2.1 | #28 | #42 | `b032654c` | `29536213259` | `82e95445` | Commercial evidence boundaries | COMPLETE |
| M2.2 | #43 | #44 | `bd257311` | `29538189271` | `ac25f23a` | Party Reference Directory | COMPLETE |
| Validation hardening | #52 | #53 | `6b89b54d` | `29749033054` | `ff15b708` | PowerShell failure propagation | COMPLETE |
| Status reconciliation | #54 | #55 | `0e1e45c` | `29749717131` | `f46ccda7` | State, domain map, roadmap and handoff | COMPLETE |
| M2.3a | #56 | #57 | `eff0418` | `29752419502` | `53972cc2` | Signed party-source artifact ingestion | COMPLETE |
| M2.3a hardening | #59 | #60 | `bcaeefa` | Pending | Pending | Safe multipage snapshot progress | CI IN PROGRESS |
| M2.3b | #51 / Gestudio #14 | Gestudio #15 | `4c88635e` | Pending | Pending | Gestudio source emitter | VALIDATED LOCALLY |

## Risks and blockers

1. Public visibility blocks proprietary vertical implementation under ADR 0002.
2. Issue #51 requires coordinated merge and deployment work; this repository
   contains only synthetic fixtures and must not copy profiles or connect to source databases.
3. Docker is mandatory for local integration evidence.
4. Host `java`/Maven JDK resolution differs; Maven's Java 21 result is authoritative for repository validation.
5. No deployment, backup or recovery environment exists; production readiness is not claimed.

## Next action

1. Finish exact-head CI for platform PR #60 and Gestudio PR #15.
2. Keep #51 open after Gestudio if its checklist still includes Scalaris or
   production operation.
3. Add Scalaris independently only after its tenant mapping is explicit.
4. Treat the integration as PENDING DEPLOYMENT after merge; local evidence is not
   production evidence.

## Recovery

Documentation changes are reversible by a normal revert. Runtime migrations V1-V8 are immutable after merge; operational schema corrections require a new forward migration.
