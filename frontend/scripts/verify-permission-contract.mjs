import { readFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const repositoryRoot = resolve(scriptDirectory, '../..')
const canonicalPath = resolve(repositoryRoot, 'contracts/authorization/permissions.json')
const frontendPath = resolve(
  repositoryRoot,
  'frontend/apps/platform-shell/src/authorization/permissions.ts',
)

const canonical = JSON.parse(await readFile(canonicalPath, 'utf8'))
const frontendSource = await readFile(frontendPath, 'utf8')
const tuple = frontendSource.match(/permissionCodes\s*=\s*\[([\s\S]*?)\]\s*as const/)

if (!tuple) {
  throw new Error('Unable to find the permissionCodes tuple')
}

const frontendCodes = [...tuple[1].matchAll(/'([^']+)'/g)].map((match) => match[1])
const canonicalCodes = canonical.map((permission) => permission.code)

const duplicateCodes = canonicalCodes.filter(
  (code, index) => canonicalCodes.indexOf(code) !== index,
)
if (duplicateCodes.length > 0) {
  throw new Error(`Canonical permission codes are duplicated: ${duplicateCodes.join(', ')}`)
}

const expected = [...canonicalCodes].sort()
const actual = [...frontendCodes].sort()

if (JSON.stringify(expected) !== JSON.stringify(actual)) {
  const missing = expected.filter((code) => !actual.includes(code))
  const unexpected = actual.filter((code) => !expected.includes(code))
  throw new Error(
    `Frontend permission contract mismatch. Missing: ${missing.join(', ') || 'none'}. ` +
      `Unexpected: ${unexpected.join(', ') || 'none'}.`,
  )
}

console.log(`Verified ${expected.length} frontend permission codes.`)
