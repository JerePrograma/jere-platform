package com.jereplatform.kernel.reliability.api;

import com.jereplatform.kernel.tenancy.api.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record OutboxMessage(
    UUID id,
    TenantId tenantId,
    String aggregateType,
    String aggregateId,
    String eventType,
    String payloadJson,
    Map<String, String> headers,
    int attempt,
    int maxAttempts,
    UUID claimToken,
    Instant createdAt
) {

    public OutboxMessage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(claimToken, "claimToken must not be null");
        headers = Map.copyOf(headers);
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
