package com.jereplatform.kernel.reliability.api;

import java.time.Instant;
import java.util.UUID;

public record OutboxFailureView(
    UUID id,
    String aggregateType,
    String aggregateId,
    String eventType,
    int attempts,
    int maxAttempts,
    String lastErrorCode,
    Instant createdAt
) {
}
