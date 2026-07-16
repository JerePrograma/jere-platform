package com.jereplatform.commercial.parties.api;

public record PartySourceRecord(
    String sourceType,
    String sourceId,
    String displayName,
    PartyLifecycleStatus status
) {

    public PartySourceRecord {
        sourceType = requireCode(sourceType, "sourceType", 40);
        sourceId = requireText(sourceId, "sourceId", 160);
        displayName = requireText(displayName, "displayName", 200);
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }

    private static String requireCode(String value, String field, int maximumLength) {
        var normalized = requireText(value, field, maximumLength).toUpperCase();
        if (!normalized.matches("[A-Z][A-Z0-9_]{1,39}")) {
            throw new IllegalArgumentException(field + " must match [A-Z][A-Z0-9_]{1,39}");
        }
        return normalized;
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
