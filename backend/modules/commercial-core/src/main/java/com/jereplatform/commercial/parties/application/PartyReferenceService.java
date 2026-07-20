package com.jereplatform.commercial.parties.application;

import com.jereplatform.commercial.parties.api.PartyId;
import com.jereplatform.commercial.parties.api.PartyImportResult;
import com.jereplatform.commercial.parties.api.PartyInactiveException;
import com.jereplatform.commercial.parties.api.PartyMutationType;
import com.jereplatform.commercial.parties.api.PartyRef;
import com.jereplatform.commercial.parties.api.PartyReferenceNotFoundException;
import com.jereplatform.commercial.parties.api.PartyReferenceView;
import com.jereplatform.commercial.parties.api.PartySourceAdapter;
import com.jereplatform.commercial.parties.api.PartySourceBatchImportResult;
import com.jereplatform.commercial.parties.api.PartySourceRecord;
import com.jereplatform.commercial.parties.api.PartySourceType;
import com.jereplatform.commercial.parties.internal.persistence.PartyReferenceStore;
import com.jereplatform.kernel.reliability.api.OutboxEventDraft;
import com.jereplatform.kernel.reliability.api.ReliableCommand;
import com.jereplatform.kernel.reliability.api.ReliableExecutionResult;
import com.jereplatform.kernel.reliability.application.OutboxService;
import com.jereplatform.kernel.reliability.application.ReliableCommandExecutor;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartyReferenceService {

    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(30);
    private static final Duration EXPORT_IDEMPOTENCY_RETENTION = Duration.ofDays(365);
    private static final int MAX_EXPORT_RECORDS = 1_000;

    private final PartyReferenceStore store;
    private final ReliableCommandExecutor reliableCommands;
    private final OutboxService outboxService;
    private final PartySourceSnapshotService snapshots;
    private final Clock clock;

    public PartyReferenceService(
        PartyReferenceStore store,
        ReliableCommandExecutor reliableCommands,
        OutboxService outboxService,
        PartySourceSnapshotService snapshots,
        Clock clock
    ) {
        this.store = store;
        this.reliableCommands = reliableCommands;
        this.outboxService = outboxService;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    public ReliableExecutionResult<PartyImportResult> importRecord(
        TenantContext context,
        PartySourceRecord record,
        String idempotencyKey
    ) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(record, "record must not be null");
        var sourceType = requireSourceType(record.sourceType());
        var normalized = new PartySourceRecord(
            sourceType.name(),
            record.sourceId(),
            record.displayName(),
            record.status()
        );
        var command = new ReliableCommand(
            "PARTY_REFERENCE_IMPORT",
            idempotencyKey,
            canonicalRequest(normalized),
            "PARTY_REFERENCE_IMPORT",
            "PARTY_REFERENCE",
            normalized.sourceId(),
            Map.of(
                "sourceType", normalized.sourceType(),
                "status", normalized.status().name()
            ),
            IDEMPOTENCY_RETENTION
        );
        return reliableCommands.execute(
            context,
            command,
            PartyImportResult.class,
            () -> importInsideTransaction(context, normalized)
        );
    }

    public ReliableExecutionResult<PartyImportResult> importFromAdapter(
        TenantContext context,
        PartySourceAdapter adapter,
        String sourceId,
        String idempotencyKey
    ) {
        Objects.requireNonNull(adapter, "adapter must not be null");
        var expectedType = requireSourceType(adapter.sourceType());
        var loaded = Objects.requireNonNull(
            adapter.load(sourceId),
            "Party source adapter returned no record"
        );
        if (!expectedType.name().equals(loaded.sourceType())) {
            throw new IllegalArgumentException("Party source adapter returned a different source type");
        }
        if (!sourceId.trim().equals(loaded.sourceId())) {
            throw new IllegalArgumentException("Party source adapter returned a different source identifier");
        }
        return importRecord(context, loaded, idempotencyKey);
    }

    public ReliableExecutionResult<PartySourceBatchImportResult> importRecords(
        TenantContext context,
        String sourceType,
        String checkpoint,
        String nextCursor,
        boolean fullSnapshot,
        List<PartySourceRecord> records
    ) {
        return importRecords(
            context,
            sourceType,
            checkpoint,
            nextCursor,
            null,
            null,
            null,
            fullSnapshot,
            records
        );
    }

    public ReliableExecutionResult<PartySourceBatchImportResult> importRecords(
        TenantContext context,
        String sourceType,
        String checkpoint,
        String nextCursor,
        Integer pageNumber,
        Integer pageCount,
        String payloadHash,
        boolean fullSnapshot,
        List<PartySourceRecord> records
    ) {
        Objects.requireNonNull(context, "context must not be null");
        var approvedType = requireSourceType(sourceType);
        var normalizedCheckpoint = requireText(checkpoint, "checkpoint", 160);
        var normalizedNextCursor = nextCursor == null
            ? null
            : requireText(nextCursor, "nextCursor", 500);
        if (records == null || records.size() > MAX_EXPORT_RECORDS) {
            throw new IllegalArgumentException("records must contain at most 1000 records");
        }
        if (fullSnapshot && normalizedNextCursor != null) {
            throw new IllegalArgumentException("A full snapshot must not declare a next cursor");
        }
        validatePageMetadata(pageNumber, pageCount, payloadHash, normalizedNextCursor, fullSnapshot);

        var normalizedRecords = new ArrayList<PartySourceRecord>(records.size());
        var sourceIds = new HashSet<String>();
        for (var record : records) {
            Objects.requireNonNull(record, "records must not contain null values");
            var normalized = new PartySourceRecord(
                approvedType.name(),
                record.sourceId(),
                record.displayName(),
                record.status()
            );
            if (!approvedType.name().equals(record.sourceType())) {
                throw new IllegalArgumentException("Record source type does not match export source type");
            }
            if (!sourceIds.add(normalized.sourceId())) {
                throw new IllegalArgumentException("Export records contain duplicate source identifiers");
            }
            normalizedRecords.add(normalized);
        }
        normalizedRecords.sort(Comparator.comparing(PartySourceRecord::sourceId));

        var command = new ReliableCommand(
            "PARTY_SOURCE_EXPORT_IMPORT",
            approvedType.name()
                + ":" + normalizedCheckpoint
                + ":" + (normalizedNextCursor == null ? "END" : normalizedNextCursor),
            canonicalExportRequest(
                approvedType.name(),
                normalizedCheckpoint,
                normalizedNextCursor,
                pageNumber,
                pageCount,
                payloadHash,
                fullSnapshot,
                normalizedRecords
            ),
            "PARTY_SOURCE_EXPORT_IMPORT",
            "PARTY_SOURCE_EXPORT",
            normalizedCheckpoint,
            Map.of(
                "sourceType", approvedType.name(),
                "recordCount", Integer.toString(normalizedRecords.size()),
                "fullSnapshot", Boolean.toString(fullSnapshot)
            ),
            EXPORT_IDEMPOTENCY_RETENTION
        );
        return reliableCommands.execute(
            context,
            command,
            PartySourceBatchImportResult.class,
            () -> {
                if (pageNumber != null) {
                    snapshots.accept(
                        context,
                        approvedType.name(),
                        normalizedCheckpoint,
                        pageNumber,
                        pageCount,
                        payloadHash,
                        normalizedNextCursor,
                        fullSnapshot,
                        normalizedRecords.stream().map(PartySourceRecord::sourceId).toList()
                    );
                }
                return importBatchInsideTransaction(context, normalizedRecords);
            }
        );
    }

    @Transactional(readOnly = true)
    public PartyReferenceView find(TenantContext context, PartyId partyId) {
        return store.findById(context.tenantId().value(), partyId)
            .orElseThrow(PartyReferenceNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public PartyReferenceView findBySource(
        TenantContext context,
        String sourceType,
        String sourceId
    ) {
        var type = requireSourceType(sourceType);
        return store.findBySourceKey(
            context.tenantId().value(),
            type.name(),
            requireSourceId(sourceId)
        ).orElseThrow(PartyReferenceNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public PartyReferenceView requireActive(TenantContext context, PartyId partyId) {
        var party = find(context, partyId);
        if (!party.active()) {
            throw new PartyInactiveException();
        }
        return party;
    }

    @Transactional(readOnly = true)
    public PartyRef snapshotForNewOperation(TenantContext context, PartyId partyId) {
        return requireActive(context, partyId).snapshot();
    }

    @Transactional(readOnly = true)
    public List<PartyReferenceView> search(
        TenantContext context,
        String query,
        boolean includeInactive,
        int limit
    ) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return store.search(context.tenantId().value(), query, includeInactive, limit);
    }

    private PartyImportResult importInsideTransaction(
        TenantContext context,
        PartySourceRecord record
    ) {
        var tenantId = context.tenantId().value();
        store.lockSourceKey(tenantId, record.sourceType(), record.sourceId());
        var existing = store.findBySourceKey(tenantId, record.sourceType(), record.sourceId());
        if (existing.isEmpty()) {
            var created = store.insert(
                tenantId,
                PartyId.random(),
                record.sourceType(),
                record.sourceId(),
                record.displayName(),
                record.status(),
                clock.instant()
            );
            enqueueChanged(context, created, PartyMutationType.CREATED);
            return new PartyImportResult(created, PartyMutationType.CREATED);
        }

        var current = existing.get();
        if (current.currentDisplayName().equals(record.displayName())
            && current.status() == record.status()) {
            return new PartyImportResult(current, PartyMutationType.UNCHANGED);
        }

        var updated = store.update(
            tenantId,
            current.id(),
            record.displayName(),
            record.status(),
            clock.instant()
        );
        enqueueChanged(context, updated, PartyMutationType.UPDATED);
        return new PartyImportResult(updated, PartyMutationType.UPDATED);
    }

    private PartySourceBatchImportResult importBatchInsideTransaction(
        TenantContext context,
        List<PartySourceRecord> records
    ) {
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (var record : records) {
            var result = importInsideTransaction(context, record);
            switch (result.mutation()) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
        }
        return new PartySourceBatchImportResult(records.size(), created, updated, unchanged);
    }

    private void enqueueChanged(
        TenantContext context,
        PartyReferenceView party,
        PartyMutationType mutation
    ) {
        outboxService.enqueue(context, new OutboxEventDraft(
            "PARTY_REFERENCE",
            party.id().value().toString(),
            mutation == PartyMutationType.CREATED
                ? "commercial.party-reference.created"
                : "commercial.party-reference.updated",
            Map.of(
                "partyId", party.id().value().toString(),
                "sourceType", party.sourceType(),
                "sourceId", party.sourceId(),
                "currentDisplayName", party.currentDisplayName(),
                "status", party.status().name()
            ),
            Map.of("correlationId", context.correlationId().toString()),
            10
        ));
    }

    private static byte[] canonicalRequest(PartySourceRecord record) {
        return String.join(
            "\n",
            record.sourceType(),
            record.sourceId(),
            record.displayName(),
            record.status().name()
        ).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] canonicalExportRequest(
        String sourceType,
        String checkpoint,
        String nextCursor,
        Integer pageNumber,
        Integer pageCount,
        String payloadHash,
        boolean fullSnapshot,
        List<PartySourceRecord> records
    ) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                writeText(output, sourceType);
                writeText(output, checkpoint);
                writeText(output, nextCursor);
                output.writeInt(pageNumber == null ? -1 : pageNumber);
                output.writeInt(pageCount == null ? -1 : pageCount);
                writeText(output, payloadHash);
                output.writeBoolean(fullSnapshot);
                output.writeInt(records.size());
                for (var record : records) {
                    writeText(output, record.sourceId());
                    writeText(output, record.displayName());
                    writeText(output, record.status().name());
                }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        var encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static PartySourceType requireSourceType(String sourceType) {
        return PartySourceType.fromCode(sourceType)
            .orElseThrow(() -> new IllegalArgumentException("Unknown party source type"));
    }

    private static String requireSourceId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        var normalized = sourceId.trim();
        if (normalized.length() > 160) {
            throw new IllegalArgumentException("sourceId exceeds 160 characters");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        var normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " exceeds " + maximumLength + " characters");
        }
        return normalized;
    }

    private static void validatePageMetadata(
        Integer pageNumber,
        Integer pageCount,
        String payloadHash,
        String nextCursor,
        boolean fullSnapshot
    ) {
        if (pageNumber == null && pageCount == null && payloadHash == null) {
            return;
        }
        if (pageNumber == null || pageCount == null || payloadHash == null
            || pageNumber < 1 || pageNumber > pageCount || pageCount > 1_000
            || !payloadHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid party source page metadata");
        }
        boolean finalPage = pageNumber.equals(pageCount);
        if (finalPage != fullSnapshot || finalPage != (nextCursor == null)) {
            throw new IllegalArgumentException("Invalid party source page finality");
        }
    }
}
