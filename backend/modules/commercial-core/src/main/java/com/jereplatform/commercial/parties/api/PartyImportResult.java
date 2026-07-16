package com.jereplatform.commercial.parties.api;

public record PartyImportResult(
    PartyReferenceView party,
    PartyMutationType mutation
) {
}
