export const partySourceTypes = [
  'GESTUDIO_STUDENT',
  'SCALARIS_THIRD_PARTY',
] as const

export type PartySourceType = (typeof partySourceTypes)[number]

export interface PartyReferenceView {
  id: string
  sourceType: PartySourceType
  sourceId: string
  displayName: string
  active: boolean
}

export interface IssuedPartyRef {
  partyId: string
  displayNameSnapshot: string
}

export function canSelectParty(party: PartyReferenceView): boolean {
  return party.active
}

export function snapshotParty(party: PartyReferenceView): IssuedPartyRef {
  if (!canSelectParty(party)) {
    throw new Error('party_reference_inactive')
  }
  return Object.freeze({
    partyId: party.id,
    displayNameSnapshot: party.displayName,
  })
}

export function isPartySourceType(value: string): value is PartySourceType {
  return (partySourceTypes as readonly string[]).includes(value)
}
