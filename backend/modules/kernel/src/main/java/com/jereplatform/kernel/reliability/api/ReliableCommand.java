package com.jereplatform.kernel.reliability.api;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record ReliableCommand(
    String operationCode,
    String idempotencyKey,
    byte[] requestBytes,
    String actionCode,
    String targetType,
    String targetId,
    Map<String, String> auditMetadata,
    Duration idempotencyRetention
) {

    public ReliableCommand {
        operationCode = requireText(operationCode, "operationCode", 120);
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 500);
        requestBytes = Objects.requireNonNull(requestBytes, "requestBytes must not be null").clone();
        actionCode = requireText(actionCode, "actionCode", 120);
        targetType = requireText(targetType, "targetType", 80);
        targetId = targetId == null ? null : requireText(targetId, "targetId", 160);
        auditMetadata = Map.copyOf(Objects.requireNonNull(auditMetadata, "auditMetadata must not be null"));
        Objects.requireNonNull(idempotencyRetention, "idempotencyRetention must not be null");
        if (idempotencyRetention.isNegative() || idempotencyRetention.isZero()) {
            throw new IllegalArgumentException("idempotencyRetention must be positive");
        }
    }

    @Override
    public byte[] requestBytes() {
        return requestBytes.clone();
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
