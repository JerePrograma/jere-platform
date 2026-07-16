package com.jereplatform.kernel.reliability.api;

public record ReliabilityCleanupResult(
    int deletedIdempotencyRecords,
    int deletedDispatchedOutboxEvents
) {
}
