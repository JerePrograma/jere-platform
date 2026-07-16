# ADR 0005 — Tenant-scoped RBAC, entitlements and feature flags

- Status: accepted
- Date: 2026-07-16

## Context

Authentication proves the active identity, tenant and membership. It does not answer what that membership may do, whether the tenant commercially owns a module, or whether a technical rollout is enabled.

Combining those decisions into global roles, ordinal hierarchies or JWT claims would create cross-tenant privilege leaks and stale authorization.

## Decision

1. Permissions are stable catalog codes such as `academy.students.read`.
2. A permission belongs to one module and explicitly declares whether it requires branch scope.
3. Roles belong to one tenant. There is no global ordinal hierarchy such as `ADMIN > USER`.
4. Role assignments belong to one active membership and may be tenant-wide or limited to one branch.
5. Tenant-wide permissions are effective only from tenant-wide role assignments.
6. Branch-scoped permissions are effective only for a branch already accessible to the active membership.
7. Commercial entitlements are independent from roles. A permission is ineffective when the tenant does not have an active entitlement for its module.
8. Feature flags are technical rollout controls. They never grant commercial entitlement or user permission.
9. Configuration remains a separate future concern and must not be inferred from role, entitlement or feature flag names.
10. Effective authorization is calculated against current PostgreSQL state for the active membership. It is not embedded in access tokens.
11. Backend method security is authoritative. Frontend route and action filtering are usability controls only.
12. Permission codes are maintained as a canonical JSON contract and verified against both the Java enum and the TypeScript tuple.
13. Base roles are reconciled by stable `system_key` and template version. Reconciliation may update only managed base roles.
14. Custom tenant roles have no `system_key` and are never overwritten by base-role reconciliation.
15. The initial bootstrap membership receives the managed `OWNER` role and the system `platform` entitlement only. Vertical entitlements must be granted explicitly.

## Base role matrix

The initial matrix is explicit and forward-only:

- `OWNER`: all known permissions, still constrained by active module entitlements and branch membership.
- `OPERATOR`: operational read/write permissions without role, membership, entitlement or billing-administration authority.
- `VIEWER`: selected read permissions only.

The names are labels, not hierarchy. Authorization always evaluates permission codes.

## Consequences

- A role assigned in tenant A grants nothing in tenant B.
- An owner role does not unlock Academy or Commerce until the corresponding entitlement is active.
- A branch role cannot accidentally grant a tenant-wide permission.
- Suspending an entitlement blocks the module immediately without editing roles.
- Enabling a feature flag does not unlock a module.
- Forced HTTP calls receive 401 when anonymous and 403 when authenticated but unauthorized.
- Adding or renaming permission codes requires synchronized backend and frontend contract changes.
- Authorization queries perform current-state database reads; caching may be introduced later only with explicit invalidation.

## Rejected alternatives

### Global roles on identity

Rejected because identity is global while authority is membership- and tenant-specific.

### Ordinal role hierarchy

Rejected because business permissions are not a linear ordering and would become implicit, brittle and difficult to audit.

### Entitlement inferred from role name

Rejected because commercial subscription state and user authority have different owners and lifecycles.

### Permissions embedded in JWT

Rejected because role assignments, branch scope and entitlements may change before token expiry.

### Frontend-only authorization

Rejected because hidden routes and buttons do not prevent direct HTTP calls.

### Reconcile every role by code

Rejected because customer-created roles must survive upgrades. Only rows with managed `system_key` participate in base-role reconciliation.
