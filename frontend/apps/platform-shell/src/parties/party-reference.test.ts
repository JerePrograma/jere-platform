import { describe, expect, it } from 'vitest'
import {
  canSelectParty,
  isPartySourceType,
  snapshotParty,
  type PartyReferenceView,
} from './party-reference'

const activeParty: PartyReferenceView = {
  id: '43a79e9c-bf74-4e5b-afbd-56596f561dc2',
  sourceType: 'GESTUDIO_STUDENT',
  sourceId: '42',
  displayName: 'Ada Lovelace',
  active: true,
}

describe('party reference contract', () => {
  it('allows only approved source types', () => {
    expect(isPartySourceType('GESTUDIO_STUDENT')).toBe(true)
    expect(isPartySourceType('SCALARIS_THIRD_PARTY')).toBe(true)
    expect(isPartySourceType('GENERIC_PERSON')).toBe(false)
  })

  it('does not allow an inactive reference for a new operation', () => {
    const inactive = { ...activeParty, active: false }
    expect(canSelectParty(inactive)).toBe(false)
    expect(() => snapshotParty(inactive)).toThrow('party_reference_inactive')
  })

  it('keeps an issued display snapshot after the current profile name changes', () => {
    const issued = snapshotParty(activeParty)
    const renamed = { ...activeParty, displayName: 'Ada Byron' }

    expect(snapshotParty(renamed).displayNameSnapshot).toBe('Ada Byron')
    expect(issued).toEqual({
      partyId: activeParty.id,
      displayNameSnapshot: 'Ada Lovelace',
    })
  })

  it('exposes no vertical profile fields', () => {
    const issued = snapshotParty(activeParty)
    expect(Object.keys(issued).sort()).toEqual([
      'displayNameSnapshot',
      'partyId',
    ])
  })
})
