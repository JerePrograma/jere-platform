package com.jereplatform.commercial.parties.api;

public record PartyReconciliationFinding(
    String code,
    String sourceType,
    String sourceId,
    String detail
) {
}
