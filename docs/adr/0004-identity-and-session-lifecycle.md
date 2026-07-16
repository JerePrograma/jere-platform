# ADR 0004 — Identity credentials and session lifecycle

- Status: accepted
- Date: 2026-07-16

## Context

The platform needs authentication before RBAC can be migrated. Authentication must work across multiple tenants without turning a global identity into a global authorization record. It must also support immediate revocation, safe browser storage and refresh replay detection.

## Decision

1. A global `identity` represents an authenticated subject. Tenant access continues to come exclusively from an active `membership`.
2. Password credentials are stored separately from identity and use Spring Security's delegating adaptive password encoder. Plaintext passwords are never persisted.
3. Each credential owns an `invalidation_version`. Password replacement or credential disabling increments this value and invalidates existing access and refresh sessions.
4. An authenticated login selects one tenant and creates a persistent `session_family` bound to exactly one identity, tenant and membership.
5. Access tokens are short-lived signed JWTs. They contain only session references and identifiers; they do not contain roles, entitlements or mutable authorization decisions.
6. Every bearer request validates the referenced session family, credential version, tenant and membership against current PostgreSQL state before creating the authenticated principal.
7. Refresh tokens are 256-bit random values. Only their SHA-256 digest is stored.
8. Refresh tokens are one-time credentials. Rotation occurs under pessimistic database locks.
9. Reuse of a consumed refresh token marks the complete family as compromised and revokes every active successor.
10. Refresh tokens are sent only through an `HttpOnly`, `SameSite=Strict` cookie scoped to `/api/auth`. Refresh and logout also require a custom intent header.
11. Logout revokes the complete session family, not only the presented token.
12. Tenant context is derived from the validated bearer session. A request cannot switch tenant by changing `X-Tenant-Code`, even when the identity has membership in both tenants.
13. Initial administration is a one-time transactional bootstrap guarded by a deployment-provided secret and a locked PostgreSQL singleton. It does not depend on demo seeds or default passwords.
14. When no JWT secret is configured, development starts with an ephemeral key that is never logged. Production must provide a stable secret of at least 32 bytes.

## Consequences

- Membership or credential revocation takes effect immediately, even before access-token expiry.
- Access validation performs database reads. This is intentional for the first commercial scale and can later be optimized with short validated-session caching and explicit invalidation.
- Concurrent refresh attempts do not create two independently usable successors.
- RBAC remains a separate concern and will attach to memberships in ADR/issue #26.
- Browser clients must keep access tokens in memory and must never copy refresh tokens to local storage or application state.
- The bootstrap endpoint disappears operationally when `PLATFORM_BOOTSTRAP_TOKEN` is unset and becomes permanently unusable after successful initialization.

## Rejected alternatives

### Long-lived self-contained JWTs

Rejected because password changes, membership revocation and tenant suspension would remain effective only after token expiry.

### Refresh token in JSON or local storage

Rejected because script access would turn an XSS defect into a long-lived session compromise.

### Reusable opaque refresh token

Rejected because theft and replay would be difficult to distinguish and would allow parallel session continuation.

### Roles embedded in the access token

Rejected because roles and entitlements are tenant-scoped mutable authorization data, not authentication evidence.

### Global credential per tenant

Rejected because identity is global while authorization and membership remain tenant-specific. Duplicating credentials per tenant would fragment account recovery and session invalidation.
