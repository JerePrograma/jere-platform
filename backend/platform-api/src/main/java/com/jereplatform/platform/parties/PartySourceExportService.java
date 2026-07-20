package com.jereplatform.platform.parties;

import com.jereplatform.commercial.parties.api.PartyLifecycleStatus;
import com.jereplatform.commercial.parties.api.PartyReconciliationReport;
import com.jereplatform.commercial.parties.api.PartySourceBatchImportResult;
import com.jereplatform.commercial.parties.api.PartySourceCandidate;
import com.jereplatform.commercial.parties.api.PartySourceRecord;
import com.jereplatform.commercial.parties.application.PartyReconciliationService;
import com.jereplatform.commercial.parties.application.PartyReferenceService;
import com.jereplatform.commercial.parties.application.PartySourceSnapshotService;
import com.jereplatform.kernel.reliability.api.IdempotencyConflictException;
import com.jereplatform.kernel.reliability.api.IdempotencyInProgressException;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
class PartySourceExportService {

    private final SignedPartySourceExportReader reader;
    private final PartyReconciliationService reconciliationService;
    private final PartyReferenceService partyReferenceService;
    private final PartySourceSnapshotService snapshotService;
    private final MeterRegistry meterRegistry;

    PartySourceExportService(
        SignedPartySourceExportReader reader,
        PartyReconciliationService reconciliationService,
        PartyReferenceService partyReferenceService,
        PartySourceSnapshotService snapshotService,
        MeterRegistry meterRegistry
    ) {
        this.reader = reader;
        this.reconciliationService = reconciliationService;
        this.partyReferenceService = partyReferenceService;
        this.snapshotService = snapshotService;
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
        var payloadHash = sha256(body);
        var inspection = snapshotService.inspect(
            context,
            export.sourceType(),
            export.checkpoint(),
            export.pageNumber(),
            export.pageCount(),
            payloadHash,
            export.records().stream().map(PartySourceExport.SourceRecord::sourceId).toList()
        );
        var report = export.fullSnapshot()
            ? (export.pageNumber() == null
                ? reconciliationService.analyzeCompleteSnapshot(
                    context, export.sourceType(), candidates)
                : reconciliationService.analyzeCompleteSnapshot(
                    context,
                    export.sourceType(),
                    candidates,
                    inspection.completeSourceIds()))
            : reconciliationService.analyze(context, candidates);
        if (report.hasBlockingFindings()) {
            increment(export.sourceType(), "rejected", report.findings().size());
        }
        return new AnalyzedExport(export, report, payloadHash);
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
                analyzed.export().pageNumber(),
                analyzed.export().pageCount(),
                analyzed.export().pageNumber() == null ? null : analyzed.payloadHash(),
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

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    record AnalyzedExport(
        PartySourceExport export,
        PartyReconciliationReport report,
        String payloadHash
    ) {
    }

    record ImportOutcome(
        AnalyzedExport analyzed,
        PartySourceBatchImportResult result,
        boolean replayed
    ) {
    }
}
