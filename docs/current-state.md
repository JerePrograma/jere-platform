# Current Platform State

## Evidence snapshot

| Field | Verified value |
|---|---|
| Date | 2026-07-20 |
| Repository | `JerePrograma/jere-platform` |
| Default branch | `main` |
| Main commit | `f46ccda7d00fc7bb207a67d40cb38d5c19f672be` |
| Main CI | run `29749867354`, successful |
| Visibility | Public |
| License | Proprietary, all rights reserved |
| Database baseline | PostgreSQL 16, Flyway V1-V7 |

The repository is an executable foundation runtime. It is not deployed evidence, a production release, or a replacement for any source product.

## Canonical documents

- `README.md`: platform vision, product boundaries and local entrypoint.
- `docs/architecture/target-architecture.md`: target topology and module rules.
- `docs/architecture/migration-strategy.md`: capability migration process.
- `docs/domain-map.md`: current and planned bounded contexts.
- `docs/roadmap/00-foundation.md`: verified milestones and next gates.
- `docs/project-status-and-handoff.md`: operational continuation record.
- `docs/adr/`: durable decisions. Do not duplicate them in status documents.

## Classification

| Area | State | Evidence | Remaining gap |
|---|---|---|---|
| Maven modular backend | IMPLEMENTED | `kernel`, `commercial-core`, `verticals`, `platform-api`; Java 21 enforcer | `verticals` has no use case yet |
| Module boundaries | IMPLEMENTED | Five ArchUnit rules | Add rules only with a demonstrated boundary |
| Tenant and organization foundation | IMPLEMENTED | V2, tenant context filter, membership and branch services | No end-user organization administration UI |
| Identity and sessions | IMPLEMENTED | V3; login, rotation, replay detection, logout and bootstrap APIs | Recovery, MFA and rate limiting are deferred |
| Authorization and entitlements | IMPLEMENTED | V4; 19-code JSON/Java/TypeScript contract | No management UI; vertical permissions are catalog entries only |
| Audit, idempotency and outbox | IMPLEMENTED | V5-V6; append-only audit and PostgreSQL worker model | No external dispatcher or production operations integration |
| Party Reference Directory | IMPLEMENTED | V7, API, reliable imports, reconciliation, audit/outbox | Production source export adapters are issue #51 |
| Source export integration | PARTIAL | signed v1 schema, HMAC rotation, tenant binding, dry-run, atomic import and metrics | Source-owned emitters remain issue #51 |
| Vertical domains | PLACEHOLDER | `VerticalsModule` marker only | No academy, commerce, appointments or logistics use case |
| Platform shell | PARTIAL | Accessible static React shell builds | No authentication, tenant switcher, routing or API flow |
| Shared UI | PARTIAL | One card component and formatter with tests | Not a design system yet |
| Local PostgreSQL | IMPLEMENTED | Compose service and health check | Docker must be running; no scripted reset lifecycle |
| CI | IMPLEMENTED | Backend, frontend and Gitleaks jobs | No SBOM, SAST or dependency policy gate |
| Observability | PARTIAL | Health/info endpoints; correlation IDs in runtime contracts | No structured-log schema, metrics dashboard or tracing backend |
| OpenAPI | ABSENT | No OpenAPI dependency or generated contract | Add with the first external API contract that needs it |
| Deployment, backups and recovery | ABSENT | No deployment target exists | Required before production readiness |
| CRM, catalog, inventory, procurement, treasury | DOCUMENTED WITHOUT IMPLEMENTATION | Target architecture and commercial evidence analysis | Require complete vertical slices and evidence gates |
| SaaS subscriptions, documents, notifications, automation, reporting | DOCUMENTED WITHOUT IMPLEMENTATION | Target architecture only | No implementation issue is active |

## Backend inventory

The API currently exposes:

- one-time platform bootstrap;
- login, refresh rotation and logout;
- current session and authorization snapshot;
- tenant-scoped audit and reliability operations;
- tenant-scoped party-reference lookup, search, import and dry-run reconciliation;
- signed, tenant-bound party-source artifact reconciliation and atomic page import;
- Actuator health and info.

Controllers remain in `platform-api`. Application and persistence behavior is owned by `kernel` or `commercial-core`. No module shares a JPA entity or imports another module's internal packages.

Flyway ownership at V7:

| Migration | Owner | Capability |
|---|---|---|
| V1 | platform bootstrap | Schema and bootstrap marker |
| V2 | kernel/tenancy | Tenant, identity, organization, branch and membership |
| V3 | kernel/identity | Credentials, session families and refresh tokens |
| V4 | kernel/authorization | Permission catalog, roles, assignments, entitlements and flags |
| V5-V6 | kernel/reliability | Audit, idempotency and transactional outbox |
| V7 | commercial-core/parties | Tenant party references and managed-role permission advance |

## Frontend inventory

The workspace has one static platform shell and one shared UI package. TypeScript contracts cover authorization and party-reference source types. It does not yet perform login, retain an access token, select a tenant, call the API or implement product navigation. Hidden controls would not be authorization evidence; backend method security remains authoritative.

## Infrastructure and CI inventory

- `infra/compose.yaml` runs PostgreSQL 16 with a readiness health check and a named volume.
- `.env.example` contains local-only defaults and empty production secrets.
- `scripts/validate.sh` and `scripts/validate.ps1` run backend verify, locked frontend install, check and build.
- PR and main CI run Maven verify, frontend lint/typecheck/tests/build and Gitleaks.
- Dependabot is configured for Maven, npm and GitHub Actions.

The supported PowerShell validation previously allowed a failed native command to be hidden by later success. PR #53 fixed exit-code propagation and was validated locally and in CI.

## Validation evidence

On 2026-07-20, with Docker Desktop available:

| Command | Result | Evidence |
|---|---|---|
| `mvn -B -f backend/pom.xml verify` | PASS | 10 contract/architecture tests and 43 PostgreSQL integration tests; Flyway V1-V7 |
| `npm --prefix frontend ci` | PASS | 212 locked packages installed |
| `npm --prefix frontend run check` | PASS | 19 permission codes, lint, typecheck and 9 tests |
| `npm --prefix frontend run build` | PASS | platform shell and shared UI built |
| `scripts/validate.ps1` failure regression | PASS | simulated Maven exit 17 returned 17 and did not invoke npm |
| Main CI at `f46ccda` | PASS | GitHub Actions run `29749867354` |

## Risks and blockers

1. The repository is public. ADR 0002 prohibits sensitive vertical logic, customer data and regulated implementations until visibility or public/private boundaries change.
2. Issue #51 still requires source-owned emitters. The platform-side signed artifact
   receiver is implemented, but Jere Platform must never connect directly to source
   databases.
3. The global `java` executable on the audited Windows host resolves to Java 8 while Maven resolves Java 21. Repository validation is green, but direct Java commands require PATH awareness.
4. Local integration validation requires Docker. Docker absence is an environment failure and must never be reported as a passed backend gate.
5. Open Dependabot PRs #46-#48 fail frontend checks and must not be merged without compatibility work. They do not make current `main` red.

## Explicitly not implemented

There are no microservices, broker, Kubernetes manifests, production deployment,
customer migration, financial shared module, vertical UI, background notification
provider or source-owned export emitter. The platform receiver alone is not an
operational end-to-end source integration.
