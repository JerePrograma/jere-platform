# ADR 0001: Start as a Modular Monolith

- Status: Accepted
- Date: 2026-07-16

## Context

Jere Platform must support several SaaS verticals while being developed and operated primarily by one developer. Existing repositories duplicate authentication, security, persistence, cash, payments and frontend foundations. A distributed architecture would increase deployment, observability and data-consistency costs before real scale or team boundaries exist.

## Decision

The main platform will start as a modular monolith in a monorepo.

- The primary backend is one Spring Boot deployable.
- An asynchronous worker may run separately while sharing source modules.
- Modules have explicit public contracts and private persistence.
- Modules may communicate through application interfaces and internal domain events.
- External publication uses a transactional outbox.
- PostgreSQL is the initial system of record.
- Product-specific web applications may share packages without sharing all navigation or branding.

## Consequences

### Positive

- Faster delivery and simpler local development.
- Atomic transactions across capabilities that genuinely belong together.
- Lower infrastructure and incident-response burden.
- Easier refactoring while module boundaries are still being discovered.
- One canonical implementation for shared capabilities.

### Negative

- Module boundaries require architectural tests and discipline rather than network isolation.
- A careless persistence model could create hidden coupling.
- The main deployment may contain code unused by a particular product.
- Independent scaling is limited until extraction occurs.

## Extraction criteria

A module may become an independent service only when several of these are demonstrated:

- independent scaling;
- materially different security or compliance;
- fault-isolation requirement;
- independent release cadence;
- stable API or event contract;
- clear data ownership;
- sufficient operational monitoring and support.

Potential candidates, not commitments, include identity, notifications, documents, automation, integrations, search, reporting and SaaS billing. They remain modules until evidence exists.

Before extraction, the proposal must define:

- a stable versioned contract and owner;
- exclusive data ownership and a migration plan;
- authentication, authorization and tenant propagation;
- idempotency, retry and failure-isolation semantics;
- logs, metrics, tracing and incident ownership;
- deployment, rollback and contract tests;
- measured operational benefit that justifies the added cost.

## Rejected alternatives

### Microservices from the start

Rejected because the operational and consistency costs exceed current business needs.

### One repository per vertical with copied foundations

Rejected because it reproduces the current duplication problem and makes fixes diverge.

### Generic mega-ERP without module boundaries

Rejected because it would couple unrelated business rules and produce an unusable commercial proposition.
