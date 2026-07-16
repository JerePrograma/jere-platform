import type { PermissionCode } from './permissions'

export interface AuthorizationSnapshot {
  tenantPermissions: PermissionCode[]
  branchPermissions: Record<string, PermissionCode[]>
  entitlements: string[]
  featureFlags: Record<string, boolean>
}

export interface AuthorizationRequirement {
  permission: PermissionCode
  branchId?: string
  entitlement?: string
}

export interface RestrictedRoute {
  path: string
  requirement: AuthorizationRequirement
}

export function hasPermission(
  snapshot: AuthorizationSnapshot,
  permission: PermissionCode,
  branchId?: string,
): boolean {
  if (branchId) {
    return snapshot.branchPermissions[branchId]?.includes(permission) ?? false
  }
  return snapshot.tenantPermissions.includes(permission)
}

export function hasEntitlement(
  snapshot: AuthorizationSnapshot,
  moduleCode: string,
): boolean {
  return snapshot.entitlements.includes(moduleCode)
}

export function isFeatureEnabled(
  snapshot: AuthorizationSnapshot,
  featureCode: string,
): boolean {
  return snapshot.featureFlags[featureCode] === true
}

export function isAuthorized(
  snapshot: AuthorizationSnapshot,
  requirement: AuthorizationRequirement,
): boolean {
  if (
    requirement.entitlement &&
    !hasEntitlement(snapshot, requirement.entitlement)
  ) {
    return false
  }

  return hasPermission(
    snapshot,
    requirement.permission,
    requirement.branchId,
  )
}

export function visibleRoutes<T extends RestrictedRoute>(
  snapshot: AuthorizationSnapshot,
  routes: readonly T[],
): T[] {
  return routes.filter((route) => isAuthorized(snapshot, route.requirement))
}
