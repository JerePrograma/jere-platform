package com.jereplatform.kernel.reliability.api;

import java.time.Instant;
import java.util.Map;

public record ReliabilitySummary(
    Map<String, Long> outboxByStatus,
    long idempotencyInProgress,
    long auditFailures,
    Instant oldestDeliverableOutboxEvent
) {

    public ReliabilitySummary {
        outboxByStatus = Map.copyOf(outboxByStatus);
    }
}
