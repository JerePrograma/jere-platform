package com.jereplatform.commercial.parties.application;

import com.jereplatform.commercial.parties.api.PartyId;
import com.jereplatform.commercial.parties.api.PartyImportResult;
import com.jereplatform.commercial.parties.api.PartyInactiveException;
import com.jereplatform.commercial.parties.api.PartyMutationType;
import com.jereplatform.commercial.parties.api.PartyRef;
import com.jereplatform.commercial.parties.api.PartyReferenceNotFoundException;
import com.jereplatform.commercial.parties.api.PartyReferenceView;
import com.jereplatform.commercial.parties.api.PartySourceAdapter;
import com.jereplatform.commercial.parties.api.PartySourceRecord;
import com.jereplatform.commercial.parties.api.PartySourceType;
import com.jereplatform.commercial.parties.internal.persistence.PartyReferenceStore;
import com.jereplatform.kernel.reliability.api.OutboxEventDraft;
import com.jereplatform.kernel.reliability.api.ReliableCommand;
import com.jereplatform.kernel.reliability.api.ReliableExecutionResult;
import com.jereplatform.kernel.reliability.application.OutboxService;
import com.jereplatform.kernel.reliability.application.ReliableCommandExecutor;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartyReferenceService {

    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(30);

    private final PartyReferenceStore store;
    private final ReliableCommandExecutor reliableCommands;
    private final OutboxService outboxService;
    private final Clock clock;

    public PartyReferenceService(
        PartyReferenceStore store,
        ReliableCommandExecutor reliableCommands,
        OutboxService outboxService,
        Clock clock
    ) {
        this.store = store;
        this.reliableCommands = reliableCommands;
        this.outboxService = outboxService;
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
}
