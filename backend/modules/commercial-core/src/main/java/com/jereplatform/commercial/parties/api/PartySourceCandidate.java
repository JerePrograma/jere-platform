package com.jereplatform.commercial.parties.api;

public record PartySourceCandidate(
    String sourceType,
    String sourceId,
    String displayName,
    Boolean active
) {
}
