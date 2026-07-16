# Target Architecture

## Status

Proposed baseline for the foundation phase. Durable changes to this document should be recorded as Architecture Decision Records.

## Business shape

Jere Platform is an internal product platform. It supports multiple independently marketed SaaS products without exposing a single generic mega-ERP to customers.

```text
Shared technical platform
        ↓
Shared commercial capabilities
        ↓
Vertical domain modules
        ↓
Independently branded products
```

Initial product families:

- Gestudio: academies, institutes, clubs and membership-based organizations.
- Commerce: inventory, quotes, sales, cash and receivables.
- Appointments: schedules, resources, availability and reminders for non-regulated services.
- Public portals: directories, listings and lead generation.

Loans, healthcare and tax products remain separately deployed and separately persisted unless a later ADR justifies a different boundary.

## Deployment topology

The initial production topology should remain intentionally small:

```text
Browser / PWA
      |
Reverse proxy
      |
Platform API  ───── PostgreSQL
      |
Async worker ───── object storage / email / external APIs
```

The API and worker may share source modules while running as different processes. Introducing a broker, service mesh, Kubernetes or distributed tracing infrastructure is not a foundation requirement.

## Repository layout

```text
apps/
├── platform-api/
├── async-worker/
├── admin-console/
├── gestudio-web/
├── commerce-web/
├── appointments-web/
└── public-portals/

modules/
├── kernel/
│   ├── tenancy/
│   ├── identity/
│   ├── authorization/
│   ├── entitlements/
│   ├── audit/
│   ├── files/
│   ├── notifications/
│   ├── jobs/
│   └── integration-outbox/
├── commercial-core/
│   ├── parties/
│   ├── catalog/
│   ├── pricing/
│   ├── quotes/
│   ├── receivables/
│   ├── payments/
│   ├── cash/
│   ├── documents/
│   └── reporting/
└── verticals/
    ├── academy/
    ├── commerce-inventory/
    ├── appointments/
    ├── logistics/
    ├── property-listings/
    └── tourism-directory/

packages/
├── ui-kit/
├── api-client/
├── contracts/
├── form-components/
└── test-fixtures/

infra/
├── docker/
├── reverse-proxy/
├── ci/
├── backups/
├── monitoring/
└── deployment/
```

Directories should be created when executable work begins. Empty directory scaffolding has no architectural value.

## Kernel responsibilities

### Tenancy

Owns:

- tenant identity;
- organization membership;
- tenant resolution for each request;
- branch or location scope;
- tenant lifecycle and status;
- isolation enforcement hooks.

It must not own business customers, students, inventory or financial obligations.

### Identity

Owns:

- user credentials;
- session and token lifecycle;
- password recovery;
- security events;
- optional external identity links.

### Authorization

Owns:

- roles;
- permissions;
- user-role assignment;
- contextual access decisions;
- branch/location scope.

### Entitlements

Owns commercial access to platform capabilities:

- subscribed plans;
- enabled modules;
- usage limits;
- add-ons;
- suspension or grace-period state.

Authorization answers whether a user may perform an action. Entitlements answer whether the tenant purchased the capability. These checks are cumulative, not interchangeable.

### Audit

Owns immutable evidence of significant actions, including actor, tenant, target, timestamp, correlation identifier and before/after data when appropriate.

### Integration outbox

Owns durable publication of events to external systems after the originating transaction commits. It avoids dual-write failures between the database and external services.

## Commercial core responsibilities

The commercial core contains reusable business concepts but must remain neutral regarding any vertical.

- Parties: people and organizations acting as customers, suppliers or contacts.
- Catalog: products and services.
- Pricing: price lists and effective dates.
- Quotes: commercial proposals before commitment.
- Receivables: obligations owed to the tenant.
- Payments: applications of money to obligations.
- Cash: cash sessions and operational money movements.
- Documents: receipts and commercial documents.
- Reporting: stable read models and exports.

A student is not a generic party with dozens of nullable academy columns. The academy module references a party and owns student-specific behavior.

## Vertical modules

Vertical modules own terminology, invariants and workflows specific to a market.

### Academy

- students or members;
- guardians;
- disciplines and plans;
- groups and schedules;
- enrollment;
- attendance;
- recurring fees;
- teacher assignments.

### Commerce inventory

- stock items;
- warehouses;
- stock movements;
- purchase requirements;
- sales and fulfillment;
- work orders where applicable.

### Appointments

- resources and professionals;
- availability rules;
- appointments;
- cancellation and no-show policies;
- reminder scheduling.

## Module interaction

Allowed interaction forms:

1. A public application interface owned by the target module.
2. A public immutable contract.
3. An internal domain event.
4. A query projection intentionally published for read use.

Forbidden interaction forms:

- importing internal implementation packages;
- sharing mutable JPA entities;
- querying another module's tables directly;
- creating bidirectional module dependencies;
- leaking tenant context through optional method parameters.

## Multi-tenancy baseline

Initial strategy: shared application and shared PostgreSQL database with tenant-discriminated tables.

Every tenant-owned record must include `tenant_id`. Uniqueness constraints must include the tenant boundary where the value is not globally unique.

Required safeguards:

- mandatory tenant context at request entry;
- tenant-aware repositories or persistence filters;
- explicit cross-tenant administrative paths;
- tests proving one tenant cannot observe or mutate another tenant's data;
- tenant identifier in audit and operational logs;
- no tenant identifier accepted blindly from request bodies when it can be resolved from trusted context.

Premium or regulated products may later use database-per-tenant or dedicated deployments without changing product-facing contracts.

## Financial integrity

Financial modules must favor immutable history:

- idempotency keys for externally retried commands;
- compensating entries instead of destructive edits;
- explicit allocation of payments to obligations;
- append-only ledgers where a ledger is justified;
- receipts generated outside the financial transaction while preserving durable pending work;
- correlation identifiers across payment, cash and document records.

## Frontend architecture

Each marketed product may have its own web application and navigation while sharing:

- design tokens;
- accessible UI primitives;
- authentication/session client;
- permission and entitlement guards;
- API client generation;
- tables, forms and validation patterns;
- observability hooks.

The frontend must not become a single shell that displays every module to every customer. Product composition should be explicit at build or route configuration level.

## When a module may become a service

Extraction requires evidence, not preference. A module is a candidate when several conditions hold:

- independent scaling requirements;
- materially different security or compliance requirements;
- need for fault isolation;
- independent release cadence;
- stable API or event contract;
- clear ownership of its data;
- sufficient monitoring, deployment and incident-response capability.

Likely future candidates include asynchronous notification delivery, document processing, webhook processing and search. They remain modules until those conditions are real.

## Explicit non-goals for the foundation

- General-purpose workflow engine.
- No-code module builder.
- Marketplace with third-party extensions.
- Event sourcing for every domain.
- Universal abstraction covering every existing repository.
- Kubernetes or service mesh.
- White-label customization through source forks.
