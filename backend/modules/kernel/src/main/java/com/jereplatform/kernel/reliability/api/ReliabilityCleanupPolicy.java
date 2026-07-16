package com.jereplatform.kernel.reliability.api;

import java.time.Duration;
import java.util.Objects;

public record ReliabilityCleanupPolicy(
    Duration dispatchedOutboxRetention,
    int batchSize
) {

    public ReliabilityCleanupPolicy {
        Objects.requireNonNull(
            dispatchedOutboxRetention,
            "dispatchedOutboxRetention must not be null"
        );
        if (dispatchedOutboxRetention.isNegative() || dispatchedOutboxRetention.isZero()) {
            throw new IllegalArgumentException("dispatchedOutboxRetention must be positive");
        }
        if (batchSize < 1 || batchSize > 10_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 10000");
        }
    }
}
