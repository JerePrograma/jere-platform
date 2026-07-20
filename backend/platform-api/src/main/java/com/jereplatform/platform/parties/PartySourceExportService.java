package com.jereplatform.platform.parties;

import com.jereplatform.commercial.parties.api.PartyLifecycleStatus;
import com.jereplatform.commercial.parties.api.PartyReconciliationReport;
import com.jereplatform.commercial.parties.api.PartySourceBatchImportResult;
import com.jereplatform.commercial.parties.api.PartySourceCandidate;
import com.jereplatform.commercial.parties.api.PartySourceRecord;
import com.jereplatform.commercial.parties.application.PartyReconciliationService;
import com.jereplatform.commercial.parties.application.PartyReferenceService;
import com.jereplatform.kernel.reliability.api.IdempotencyConflictException;
import com.jereplatform.kernel.reliability.api.IdempotencyInProgressException;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
class PartySourceExportService {

    private final SignedPartySourceExportReader reader;
    private final PartyReconciliationService reconciliationService;
    private final PartyReferenceService partyReferenceService;
    private final MeterRegistry meterRegistry;

    PartySourceExportService(
        SignedPartySourceExportReader reader,
        PartyReconciliationService reconciliationService,
        PartyReferenceService partyReferenceService,
        MeterRegistry meterRegistry
    ) {
        this.reader = reader;
        this.reconciliationService = reconciliationService;
        this.partyReferenceService = partyReferenceService;
        this.meterRegistry = meterRegistry;
    }

    AnalyzedExport reconcile(
        TenantContext context,
        String declaredSourceType,
        String signature,
        byte[] body
    ) {
        PartySourceExport export;
        try {
            export = reader.read(context, declaredSourceType, signature, body);
        } catch (PartySourceExportException failure) {
            increment(metricSource(declaredSourceType), "rejected", 1);
            throw failure;
        }

        increment(export.sourceType(), "read", export.records().size());
        var candidates = candidates(export);
        var report = export.fullSnapshot()
            ? reconciliationService.analyzeCompleteSnapshot(
                context, export.sourceType(), candidates)
            : reconciliationService.analyze(context, candidates);
        if (report.hasBlockingFindings()) {
            increment(export.sourceType(), "rejected", report.findings().size());
        }
        return new AnalyzedExport(export, report);
    }

    ImportOutcome importExport(
        TenantContext context,
        String declaredSourceType,
        String signature,
        byte[] body
    ) {
        var analyzed = reconcile(context, declaredSourceType, signature, body);
        if (analyzed.report().hasBlockingFindings()) {
            return new ImportOutcome(analyzed, null, false);
        }

        try {
            var result = partyReferenceService.importRecords(
                context,
                analyzed.export().sourceType(),
                analyzed.export().checkpoint(),
                analyzed.export().nextCursor(),
                analyzed.export().fullSnapshot(),
                analyzed.export().records().stream().map(record -> new PartySourceRecord(
                    analyzed.export().sourceType(),
                    record.sourceId(),
                    record.displayName(),
                    Boolean.TRUE.equals(record.active())
                        ? PartyLifecycleStatus.ACTIVE
                        : PartyLifecycleStatus.INACTIVE
                )).toList()
            );
            if (result.replayed()) {
                increment(
                    analyzed.export().sourceType(),
                    "replayed",
                    result.value().totalRecords()
                );
            } else {
                increment(
                    analyzed.export().sourceType(),
                    "imported",
                    result.value().createdRecords() + result.value().updatedRecords()
                );
                increment(
                    analyzed.export().sourceType(),
                    "unchanged",
                    result.value().unchangedRecords()
                );
            }
            return new ImportOutcome(analyzed, result.value(), result.replayed());
        } catch (IdempotencyConflictException | IdempotencyInProgressException conflict) {
            increment(analyzed.export().sourceType(), "conflicted", 1);
            throw conflict;
        }
    }

    private static List<PartySourceCandidate> candidates(PartySourceExport export) {
        return export.records().stream().map(record -> new PartySourceCandidate(
            export.sourceType(),
            record.sourceId(),
            record.displayName(),
            record.active()
        )).toList();
    }

    private void increment(String sourceType, String outcome, double amount) {
        if (amount == 0) {
            return;
        }
        meterRegistry.counter(
            "jere.party_source_export.records",
            "source",
            sourceType,
            "outcome",
            outcome
        ).increment(amount);
    }

    private static String metricSource(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return "UNKNOWN";
        }
        var normalized = sourceType.trim().toUpperCase(Locale.ROOT);
        return normalized.length() > 40 ? "UNKNOWN" : normalized;
    }

    record AnalyzedExport(PartySourceExport export, PartyReconciliationReport report) {
    }

    record ImportOutcome(
        AnalyzedExport analyzed,
        PartySourceBatchImportResult result,
        boolean replayed
    ) {
    }
}
