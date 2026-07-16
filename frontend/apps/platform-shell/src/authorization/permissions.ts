export const permissionCodes = [
  'platform.session.read',
  'platform.memberships.manage',
  'platform.roles.manage',
  'platform.entitlements.read',
  'platform.audit.read',
  'platform.reliability.read',
  'platform.reliability.manage',
  'academy.students.read',
  'academy.students.write',
  'academy.attendance.manage',
  'academy.billing.read',
  'academy.billing.manage',
  'commerce.customers.read',
  'commerce.inventory.read',
  'commerce.inventory.manage',
  'commerce.cash.read',
  'commerce.cash.manage',
] as const

export type PermissionCode = (typeof permissionCodes)[number]

export function isPermissionCode(value: string): value is PermissionCode {
  return (permissionCodes as readonly string[]).includes(value)
}
