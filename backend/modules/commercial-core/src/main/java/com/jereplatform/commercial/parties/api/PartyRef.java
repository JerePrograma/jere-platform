package com.jereplatform.commercial.parties.api;

import java.util.Objects;

public record PartyRef(
    PartyId partyId,
    String displayNameSnapshot
) {

    public PartyRef {
        Objects.requireNonNull(partyId, "partyId must not be null");
        displayNameSnapshot = requireText(displayNameSnapshot, "displayNameSnapshot", 200);
    }

    private static String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        var normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " exceeds " + maximumLength + " characters");
        }
        return normalized;
    }
}
