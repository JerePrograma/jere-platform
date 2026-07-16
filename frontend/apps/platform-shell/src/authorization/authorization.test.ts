import { describe, expect, it } from 'vitest'
import {
  hasEntitlement,
  hasPermission,
  isAuthorized,
  isFeatureEnabled,
  visibleRoutes,
  type AuthorizationSnapshot,
  type RestrictedRoute,
} from './authorization'

const snapshot: AuthorizationSnapshot = {
  tenantPermissions: ['platform.session.read'],
  branchPermissions: {
    'branch-a': ['academy.students.read'],
    'branch-b': [],
  },
  entitlements: ['platform', 'academy'],
  featureFlags: {
    'academy.new-attendance-ui': true,
    'commerce.cycle-counts': false,
  },
}

describe('authorization evaluator', () => {
  it('keeps tenant and branch permissions separate', () => {
    expect(hasPermission(snapshot, 'platform.session.read')).toBe(true)
    expect(hasPermission(snapshot, 'academy.students.read')).toBe(false)
    expect(
      hasPermission(snapshot, 'academy.students.read', 'branch-a'),
    ).toBe(true)
    expect(
      hasPermission(snapshot, 'academy.students.read', 'branch-b'),
    ).toBe(false)
  })

  it('requires entitlement independently from permission', () => {
    expect(
      isAuthorized(snapshot, {
        permission: 'academy.students.read',
        branchId: 'branch-a',
        entitlement: 'academy',
      }),
    ).toBe(true)

    expect(
      isAuthorized(snapshot, {
        permission: 'academy.students.read',
        branchId: 'branch-a',
        entitlement: 'commerce',
      }),
    ).toBe(false)
  })

  it('filters routes using the same explicit requirements used by actions', () => {
    const routes = [
      {
        path: '/session',
        requirement: { permission: 'platform.session.read' },
      },
      {
        path: '/academy/branch-a/students',
        requirement: {
          permission: 'academy.students.read',
          branchId: 'branch-a',
          entitlement: 'academy',
        },
      },
      {
        path: '/academy/branch-b/students',
        requirement: {
          permission: 'academy.students.read',
          branchId: 'branch-b',
          entitlement: 'academy',
        },
      },
      {
        path: '/commerce/inventory',
        requirement: {
          permission: 'commerce.inventory.read',
          branchId: 'branch-a',
          entitlement: 'commerce',
        },
      },
    ] satisfies readonly RestrictedRoute[]

    expect(visibleRoutes(snapshot, routes).map((route) => route.path)).toEqual([
      '/session',
      '/academy/branch-a/students',
    ])
  })

  it('keeps feature flags distinct from entitlements', () => {
    expect(hasEntitlement(snapshot, 'academy')).toBe(true)
    expect(isFeatureEnabled(snapshot, 'academy.new-attendance-ui')).toBe(true)
    expect(isFeatureEnabled(snapshot, 'commerce.cycle-counts')).toBe(false)
    expect(hasEntitlement(snapshot, 'commerce')).toBe(false)
  })
})
