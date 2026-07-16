package com.jereplatform.commercial.parties.api;

import java.util.List;

public record PartyReconciliationReport(
    int totalCandidates,
    int validCandidates,
    int newMappings,
    int unchangedMappings,
    int changedNames,
    int statusChanges,
    List<PartyReconciliationFinding> findings
) {

    public PartyReconciliationReport {
        findings = List.copyOf(findings);
    }

    public boolean hasBlockingFindings() {
        return !findings.isEmpty();
    }
}
