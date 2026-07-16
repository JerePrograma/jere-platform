package com.jereplatform.kernel.reliability.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventView(
    UUID id,
    UUID actorIdentityId,
    UUID actorMembershipId,
    String actionCode,
    String targetType,
    String targetId,
    String result,
    String failureCode,
    UUID correlationId,
    Map<String, String> metadata,
    Instant occurredAt
) {
}
