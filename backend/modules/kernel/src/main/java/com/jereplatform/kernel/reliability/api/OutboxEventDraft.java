package com.jereplatform.kernel.reliability.api;

import java.util.Map;
import java.util.Objects;

public record OutboxEventDraft(
    String aggregateType,
    String aggregateId,
    String eventType,
    Object payload,
    Map<String, String> headers,
    int maxAttempts
) {

    public OutboxEventDraft {
        aggregateType = requireText(aggregateType, "aggregateType", 80);
        aggregateId = requireText(aggregateId, "aggregateId", 160);
        eventType = requireText(eventType, "eventType", 160);
        Objects.requireNonNull(payload, "payload must not be null");
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers must not be null"));
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 100");
        }
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
