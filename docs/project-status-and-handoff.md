# Project Status and Handoff

## Active snapshot

| Field | Value |
|---|---|
| Date | 2026-07-20 |
| Main SHA | `ff15b7081fc66ec13d645c91238c4b9f6d37c703` |
| Main CI | run `29749213799`, PASS |
| Active branch | `agent/reconcile-platform-status` |
| Active issue | #54 |
| Active PR | Pending |
| Active head | Pending |
| Next product increment | #51, production party-source export adapters |

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
- No production customer data, secrets or source database connection is present.

## Current validation

| Gate | Result | Evidence |
|---|---|---|
| Local backend | PASS | Maven verify: 9 contract/architecture tests and 41 integration tests |
| Local frontend | PASS | permission contract, lint, typecheck, 9 tests and build |
| PowerShell failure propagation | PASS | simulated exit 17 stopped before npm |
| PR #53 CI | PASS | run `29749033054` at head `6b89b54` |
| Main post-merge CI | PASS | run `29749213799` at `ff15b70` |

## Durable decisions

- ADR 0001: modular monolith first; services require operational evidence.
- ADR 0002: public proprietary foundation; sensitive vertical logic is blocked.
- ADR 0003: global identity, tenant membership and trusted tenant context.
- ADR 0004: revocable database-validated sessions and rotating refresh tokens.
- ADR 0005: permissions, entitlements, feature flags and configuration are separate.
- ADR 0006: audit, idempotency and PostgreSQL transactional outbox.
- ADR 0007: commercial sharing requires current product evidence.
- ADR 0008: vertical profiles stay owned by source products; only party references are shared.

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
| Status reconciliation | #54 | Pending | Pending | Pending | Pending | State, domain map, roadmap and handoff | ACTIVE |
| M2.3 | #51 | Pending | Pending | Pending | Pending | Production source export adapters | NEXT |

## Risks and blockers

1. Public visibility blocks proprietary vertical implementation under ADR 0002.
2. Issue #51 requires coordinated source-export contracts but this repository must not copy profiles or connect to source databases.
3. Docker is mandatory for local integration evidence.
4. Host `java`/Maven JDK resolution differs; Maven's Java 21 result is authoritative for repository validation.
5. No deployment, backup or recovery environment exists; production readiness is not claimed.

## Next action

1. Finish issue #54, validate documentation diff, open a draft PR and record its exact head/run.
2. Merge only with the validated head SHA.
3. Start issue #51 from the resulting `main`.
4. Revalidate frozen source commits and current source export surfaces.
5. Implement one authenticated, resumable platform consumption path without adding direct database access or vertical profile fields.

## Recovery

Documentation changes are reversible by a normal revert. Runtime migrations V1-V7 are immutable; operational schema corrections require a new forward migration.
