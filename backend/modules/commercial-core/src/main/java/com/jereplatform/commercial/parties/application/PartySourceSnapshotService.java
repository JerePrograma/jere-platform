package com.jereplatform.commercial.parties.application;

import static com.jereplatform.commercial.parties.api.PartySourceSnapshotException.Reason.PAGE_CONFLICT;
import static com.jereplatform.commercial.parties.api.PartySourceSnapshotException.Reason.PAGE_OUT_OF_ORDER;
import static com.jereplatform.commercial.parties.api.PartySourceSnapshotException.Reason.SNAPSHOT_NOT_COMPLETE;
import static com.jereplatform.commercial.parties.api.PartySourceSnapshotException.Reason.SNAPSHOT_NOT_FOUND;

import com.jereplatform.commercial.parties.api.PartySourceSnapshotException;
import com.jereplatform.commercial.parties.internal.persistence.PartySourceSnapshotStore;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PartySourceSnapshotService {

    private final PartySourceSnapshotStore store;
    private final Clock clock;

    public PartySourceSnapshotService(PartySourceSnapshotStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Inspection inspect(
        TenantContext context,
        String sourceType,
        String checkpoint,
        Integer pageNumber,
        Integer pageCount,
        String payloadHash,
        List<String> sourceIds
    ) {
        if (pageNumber == null) {
            return new Inspection(Set.of());
        }
        var snapshot = store.find(context.tenantId().value(), sourceType, checkpoint);
        var pageHash = store.findPageHash(
            context.tenantId().value(), sourceType, checkpoint, pageNumber);
        if (pageHash.isPresent()) {
            if (!pageHash.get().equals(payloadHash)) {
                throw new PartySourceSnapshotException(PAGE_CONFLICT);
            }
            return new Inspection(pageNumber.equals(pageCount)
                ? Set.copyOf(store.findSourceIds(
                    context.tenantId().value(), sourceType, checkpoint))
                : Set.of());
        }

        validateNewPage(snapshot.orElse(null), pageNumber, pageCount);
        var normalizedSourceIds = normalizedSourceIds(sourceIds);
        var acceptedSourceIds = new HashSet<>(store.findSourceIds(
            context.tenantId().value(), sourceType, checkpoint));
        if (normalizedSourceIds.stream().anyMatch(acceptedSourceIds::contains)) {
            throw new PartySourceSnapshotException(PAGE_CONFLICT);
        }
        if (!pageNumber.equals(pageCount)) {
            return new Inspection(Set.of());
        }
        acceptedSourceIds.addAll(normalizedSourceIds);
        return new Inspection(Set.copyOf(acceptedSourceIds));
    }

    void accept(
        TenantContext context,
        String sourceType,
        String checkpoint,
        int pageNumber,
        int pageCount,
        String payloadHash,
        String nextCursor,
        boolean fullSnapshot,
        List<String> sourceIds
    ) {
        var tenantId = context.tenantId().value();
        store.lock(tenantId, sourceType, checkpoint);
        var snapshot = store.find(tenantId, sourceType, checkpoint);
        var pageHash = store.findPageHash(tenantId, sourceType, checkpoint, pageNumber);
        if (pageHash.isPresent()) {
            if (!pageHash.get().equals(payloadHash)) {
                throw new PartySourceSnapshotException(PAGE_CONFLICT);
            }
            return;
        }

        validateNewPage(snapshot.orElse(null), pageNumber, pageCount);
        var normalizedSourceIds = normalizedSourceIds(sourceIds);
        var acceptedSourceIds = new HashSet<>(store.findSourceIds(
            tenantId, sourceType, checkpoint));
        if (normalizedSourceIds.stream().anyMatch(acceptedSourceIds::contains)) {
            throw new PartySourceSnapshotException(PAGE_CONFLICT);
        }

        var now = clock.instant();
        if (snapshot.isEmpty()) {
            store.insertSnapshot(tenantId, sourceType, checkpoint, pageCount, now);
        }
        store.insertPage(
            tenantId,
            sourceType,
            checkpoint,
            pageNumber,
            payloadHash,
            nextCursor,
            fullSnapshot,
            normalizedSourceIds,
            now
        );
        store.advance(
            tenantId,
            sourceType,
            checkpoint,
            pageNumber + 1,
            pageNumber == pageCount,
            now
        );
    }

    private static void validateNewPage(
        PartySourceSnapshotStore.Snapshot snapshot,
        int pageNumber,
        int pageCount
    ) {
        if (snapshot == null) {
            if (pageNumber != 1) {
                throw new PartySourceSnapshotException(SNAPSHOT_NOT_FOUND);
            }
            return;
        }
        if (snapshot.pageCount() != pageCount || snapshot.completed()) {
            throw new PartySourceSnapshotException(PAGE_CONFLICT);
        }
        if (snapshot.nextPage() != pageNumber) {
            throw new PartySourceSnapshotException(
                pageNumber == pageCount ? SNAPSHOT_NOT_COMPLETE : PAGE_OUT_OF_ORDER);
        }
    }

    private static List<String> normalizedSourceIds(List<String> sourceIds) {
        return sourceIds.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(sourceId -> !sourceId.isEmpty())
            .toList();
    }

    public record Inspection(Set<String> completeSourceIds) {
    }
}
