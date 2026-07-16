package com.jereplatform.commercial.parties.application;

import com.jereplatform.commercial.parties.api.PartyLifecycleStatus;
import com.jereplatform.commercial.parties.api.PartyReconciliationFinding;
import com.jereplatform.commercial.parties.api.PartyReconciliationReport;
import com.jereplatform.commercial.parties.api.PartySourceCandidate;
import com.jereplatform.commercial.parties.api.PartySourceType;
import com.jereplatform.commercial.parties.internal.persistence.PartyReferenceStore;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PartyReconciliationService {

    private final PartyReferenceStore store;

    public PartyReconciliationService(PartyReferenceStore store) {
        this.store = store;
    }

    public PartyReconciliationReport analyze(
        TenantContext context,
        List<PartySourceCandidate> candidates
    ) {
        if (candidates == null || candidates.size() > 10_000) {
            throw new IllegalArgumentException("candidates must contain at most 10000 records");
        }

        var findings = new ArrayList<PartyReconciliationFinding>();
        var normalized = new ArrayList<NormalizedCandidate>();
        var seen = new HashSet<String>();
        var duplicated = new HashSet<String>();

        for (int index = 0; index < candidates.size(); index++) {
            var candidate = candidates.get(index);
            var accepted = normalize(index, candidate, findings);
            if (accepted == null) {
                continue;
            }
            var key = accepted.sourceType() + "|" + accepted.sourceId();
            if (!seen.add(key)) {
                duplicated.add(key);
                findings.add(new PartyReconciliationFinding(
                    "DUPLICATE_SOURCE_KEY",
                    accepted.sourceType(),
                    accepted.sourceId(),
                    "The source key appears more than once in the candidate set"
                ));
                continue;
            }
            normalized.add(accepted);
        }

        int valid = 0;
        int newMappings = 0;
        int unchangedMappings = 0;
        int changedNames = 0;
        int statusChanges = 0;

        for (var candidate : normalized) {
            var key = candidate.sourceType() + "|" + candidate.sourceId();
            if (duplicated.contains(key)) {
                continue;
            }
            valid++;
            var existing = store.findBySourceKey(
                context.tenantId().value(),
                candidate.sourceType(),
                candidate.sourceId()
            );
            if (existing.isEmpty()) {
                newMappings++;
                continue;
            }
            var current = existing.get();
            boolean nameChanged = !current.currentDisplayName().equals(candidate.displayName());
            boolean statusChanged = current.status() != candidate.status();
            if (nameChanged) {
                changedNames++;
            }
            if (statusChanged) {
                statusChanges++;
            }
            if (!nameChanged && !statusChanged) {
                unchangedMappings++;
            }
        }

        return new PartyReconciliationReport(
            candidates.size(),
            valid,
            newMappings,
            unchangedMappings,
            changedNames,
            statusChanges,
            findings
        );
    }

    private static NormalizedCandidate normalize(
        int index,
        PartySourceCandidate candidate,
        List<PartyReconciliationFinding> findings
    ) {
        if (candidate == null) {
            findings.add(new PartyReconciliationFinding(
                "MISSING_CANDIDATE", null, null, "Candidate at index " + index + " is null"));
            return null;
        }

        String rawType = trim(candidate.sourceType());
        String rawId = trim(candidate.sourceId());
        String rawName = trim(candidate.displayName());
        boolean invalid = false;

        if (rawType == null) {
            findings.add(new PartyReconciliationFinding(
                "MISSING_SOURCE_TYPE", null, rawId, "sourceType is required"));
            invalid = true;
        }
        if (rawId == null) {
            findings.add(new PartyReconciliationFinding(
                "MISSING_SOURCE_ID", rawType, null, "sourceId is required"));
            invalid = true;
        } else if (rawId.length() > 160) {
            findings.add(new PartyReconciliationFinding(
                "SOURCE_ID_TOO_LONG", rawType, rawId, "sourceId exceeds 160 characters"));
            invalid = true;
        }
        if (rawName == null) {
            findings.add(new PartyReconciliationFinding(
                "MISSING_DISPLAY_NAME", rawType, rawId, "displayName is required"));
            invalid = true;
        } else if (rawName.length() > 200) {
            findings.add(new PartyReconciliationFinding(
                "DISPLAY_NAME_TOO_LONG", rawType, rawId, "displayName exceeds 200 characters"));
            invalid = true;
        }
        if (candidate.active() == null) {
            findings.add(new PartyReconciliationFinding(
                "MISSING_ACTIVE_STATE", rawType, rawId, "active state is required"));
            invalid = true;
        }

        PartySourceType sourceType = null;
        if (rawType != null) {
            sourceType = PartySourceType.fromCode(rawType).orElse(null);
            if (sourceType == null) {
                findings.add(new PartyReconciliationFinding(
                    "UNKNOWN_SOURCE_TYPE", rawType, rawId, "sourceType is not approved"));
                invalid = true;
            }
        }
        if (invalid) {
            return null;
        }

        return new NormalizedCandidate(
            sourceType.name(),
            rawId,
            rawName,
            Boolean.TRUE.equals(candidate.active())
                ? PartyLifecycleStatus.ACTIVE
                : PartyLifecycleStatus.INACTIVE
        );
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        var normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record NormalizedCandidate(
        String sourceType,
        String sourceId,
        String displayName,
        PartyLifecycleStatus status
    ) {
    }
}
