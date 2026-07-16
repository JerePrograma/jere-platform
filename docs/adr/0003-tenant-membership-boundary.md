# ADR 0003 — Tenant, identity and membership boundary

- Status: accepted
- Date: 2026-07-16

## Context

Jere Platform will host multiple commercial products and multiple customer organizations. Reusing a global user record as authorization, trusting a tenant identifier sent by the browser, or relying only on row-level security would allow accidental cross-tenant access.

The platform needs a boundary that exists before authentication, RBAC, entitlements and commercial modules are migrated.

## Decision

1. `identity` is a global reference to an authenticated subject. It contains no business role.
2. `membership` is the explicit relationship between an identity and one tenant.
3. Membership lifecycle is explicit. Revocation is retained as history and prevents future tenant activation.
4. Organizations and branches are tenant-owned.
5. Composite foreign keys include `tenant_id` so PostgreSQL rejects cross-tenant organization, branch and membership assignments.
6. Request tenant context is created from:
   - an authenticated `Principal` supplied by the security layer;
   - a requested tenant code used only as a selector;
   - a verified active membership;
   - active branch grants;
   - a correlation identifier.
7. No identity header is accepted as authentication.
8. Application queries for tenant-owned information receive a resolved `TenantContext`, not an arbitrary tenant identifier.
9. A scoped holder removes request context in `finally`/`AutoCloseable` semantics to prevent thread reuse leaks.
10. PostgreSQL constraints are a second line of defense, not a replacement for application authorization.

## Consequences

- The same organization or branch code may exist in different tenants.
- Selecting another tenant succeeds only when the authenticated identity has an active membership there.
- Future identity/session work can replace the source of `Principal` without changing tenancy contracts.
- RBAC and entitlements will attach to memberships rather than global identities.
- Branch scope can be extended without changing ownership rules.
- Cross-tenant database writes fail even if an application defect reaches persistence.

## Rejected alternatives

### Tenant ID trusted from headers or payloads

Rejected because client-controlled identifiers are selectors, not authorization evidence.

### Global roles on identity

Rejected because a role in one customer account must grant nothing in another.

### Separate database per tenant from day one

Rejected as an operational default. The model preserves tenant ownership and can later route selected tenants to isolated deployments.

### PostgreSQL row-level security as the only defense

Rejected because authorization must remain explicit and testable at application boundaries.
