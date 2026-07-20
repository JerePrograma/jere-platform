package com.jereplatform.commercial.parties.internal.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PartySourceSnapshotStore {

    private final JdbcTemplate jdbcTemplate;

    public PartySourceSnapshotStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lock(UUID tenantId, String sourceType, String checkpoint) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))"
            )) {
                statement.setString(1, tenantId + ":" + sourceType + ":" + checkpoint);
                statement.execute();
                return null;
            }
        });
    }

    public Optional<Snapshot> find(UUID tenantId, String sourceType, String checkpoint) {
        return jdbcTemplate.query(
            """
            select page_count, next_page, completed_at
              from platform.party_source_snapshot
             where tenant_id = ? and source_type = ? and checkpoint = ?
            """,
            (rs, rowNumber) -> new Snapshot(
                rs.getInt("page_count"),
                rs.getInt("next_page"),
                rs.getTimestamp("completed_at") != null
            ),
            tenantId,
            sourceType,
            checkpoint
        ).stream().findFirst();
    }

    public Optional<String> findPageHash(
        UUID tenantId,
        String sourceType,
        String checkpoint,
        int pageNumber
    ) {
        return jdbcTemplate.queryForList(
            """
            select payload_hash
              from platform.party_source_snapshot_page
             where tenant_id = ? and source_type = ? and checkpoint = ? and page_number = ?
            """,
            String.class,
            tenantId,
            sourceType,
            checkpoint,
            pageNumber
        ).stream().findFirst();
    }

    public List<String> findSourceIds(UUID tenantId, String sourceType, String checkpoint) {
        return jdbcTemplate.queryForList(
            """
            select source_id
              from platform.party_source_snapshot_record
             where tenant_id = ? and source_type = ? and checkpoint = ?
             order by source_id
            """,
            String.class,
            tenantId,
            sourceType,
            checkpoint
        );
    }

    public void insertSnapshot(
        UUID tenantId,
        String sourceType,
        String checkpoint,
        int pageCount,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            insert into platform.party_source_snapshot (
                tenant_id, source_type, checkpoint, page_count, next_page,
                created_at, updated_at
            ) values (?, ?, ?, ?, 1, ?, ?)
            """,
            tenantId,
            sourceType,
            checkpoint,
            pageCount,
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    public void insertPage(
        UUID tenantId,
        String sourceType,
        String checkpoint,
        int pageNumber,
        String payloadHash,
        String nextCursor,
        boolean fullSnapshot,
        List<String> sourceIds,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            insert into platform.party_source_snapshot_page (
                tenant_id, source_type, checkpoint, page_number, payload_hash,
                next_cursor, full_snapshot, record_count, accepted_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            tenantId,
            sourceType,
            checkpoint,
            pageNumber,
            payloadHash,
            nextCursor,
            fullSnapshot,
            sourceIds.size(),
            Timestamp.from(now)
        );
        jdbcTemplate.batchUpdate(
            """
            insert into platform.party_source_snapshot_record (
                tenant_id, source_type, checkpoint, page_number, source_id
            ) values (?, ?, ?, ?, ?)
            """,
            sourceIds,
            sourceIds.size(),
            (statement, sourceId) -> {
                statement.setObject(1, tenantId);
                statement.setString(2, sourceType);
                statement.setString(3, checkpoint);
                statement.setInt(4, pageNumber);
                statement.setString(5, sourceId);
            }
        );
    }

    public void advance(
        UUID tenantId,
        String sourceType,
        String checkpoint,
        int nextPage,
        boolean completed,
        Instant now
    ) {
        int affected = jdbcTemplate.update(
            """
            update platform.party_source_snapshot
               set next_page = ?, updated_at = ?, completed_at = ?
             where tenant_id = ? and source_type = ? and checkpoint = ?
            """,
            nextPage,
            Timestamp.from(now),
            completed ? Timestamp.from(now) : null,
            tenantId,
            sourceType,
            checkpoint
        );
        if (affected != 1) {
            throw new IllegalStateException("Party source snapshot progress disappeared");
        }
    }

    public record Snapshot(int pageCount, int nextPage, boolean completed) {
    }
}
