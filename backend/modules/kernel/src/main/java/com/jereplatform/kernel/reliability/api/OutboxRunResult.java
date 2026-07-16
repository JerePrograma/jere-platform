package com.jereplatform.kernel.reliability.api;

public record OutboxRunResult(
    int claimed,
    int dispatched,
    int scheduledForRetry,
    int movedToDead
) {
}
