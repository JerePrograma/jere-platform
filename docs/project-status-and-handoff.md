# Project Status and Handoff

## Active snapshot

| Field | Value |
|---|---|
| Date | 2026-07-20 |
| Main SHA | `53972cc2b15a1c4d1f49ac0724e1ba95c2bd29d8` |
| Main CI | run `29752682676`, PASS |
| Active branch | None after documentation closeout |
| Active issue | None; #51 is the next coordinated product issue |
| Active PR | None after documentation closeout |
| Active head | `53972cc2b15a1c4d1f49ac0724e1ba95c2bd29d8` |
| Next product increment | #51, source-owned v1 export emitters |

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

- Flyway is forward-only through V7.
- Tenant-owned uniqueness and composite foreign keys include tenant ownership where applicable.
- Canonical permission contract contains 19 codes.
- Canonical party source types are `GESTUDIO_STUDENT` and `SCALARIS_THIRD_PARTY`.
- Signed export v1 accepts only reference fields and uses reliable page replay.
- No production customer data, secrets or source database connection is present.

## Current validation

| Gate | Result | Evidence |
|---|---|---|
| Local backend | PASS | Maven verify: 10 contract/architecture tests and 43 integration tests |
| Local frontend | PASS | permission contract, lint, typecheck, 9 tests and build |
| PowerShell failure propagation | PASS | simulated exit 17 stopped before npm |
| PR #57 CI | PASS | run `29752419502` at head `eff0418` |
| Main post-merge CI | PASS | run `29752682676` at `53972cc` |

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
| M2.3b | #51 | Pending | Pending | Pending | Pending | Source-owned export emitters | NEXT |

## Risks and blockers

1. Public visibility blocks proprietary vertical implementation under ADR 0002.
2. Issue #51 requires coordinated changes in source repositories; this repository
   must not copy profiles or connect to source databases.
3. Docker is mandatory for local integration evidence.
4. Host `java`/Maven JDK resolution differs; Maven's Java 21 result is authoritative for repository validation.
5. No deployment, backup or recovery environment exists; production readiness is not claimed.

## Next action

1. Keep #51 open and coordinate one source-owned Gestudio exporter for the v1
   contract without copying its profile model.
2. Add the Scalaris exporter independently after its tenant mapping is explicit.
3. Treat the platform receiver as PARTIAL until at least one source emitter is
   deployed and exercised end to end.

## Recovery

Documentation changes are reversible by a normal revert. Runtime migrations V1-V7 are immutable; operational schema corrections require a new forward migration.
