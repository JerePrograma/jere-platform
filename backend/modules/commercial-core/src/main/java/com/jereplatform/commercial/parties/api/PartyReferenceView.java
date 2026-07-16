package com.jereplatform.commercial.parties.api;

import java.time.Instant;

public record PartyReferenceView(
    PartyId id,
    String sourceType,
    String sourceId,
    String currentDisplayName,
    PartyLifecycleStatus status,
    Instant createdAt,
    Instant updatedAt
) {

    public PartyRef snapshot() {
        return new PartyRef(id, currentDisplayName);
    }

    public boolean active() {
        return status == PartyLifecycleStatus.ACTIVE;
    }
}
