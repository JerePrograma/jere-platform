package com.jereplatform.commercial.parties.internal.persistence;

import com.jereplatform.commercial.parties.api.PartyId;
import com.jereplatform.commercial.parties.api.PartyLifecycleStatus;
import com.jereplatform.commercial.parties.api.PartyReferenceView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PartyReferenceStore {

    private static final RowMapper<PartyReferenceView> ROW_MAPPER = PartyReferenceStore::map;

    private final JdbcTemplate jdbcTemplate;

    public PartyReferenceStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockSourceKey(UUID tenantId, String sourceType, String sourceId) {
        jdbcTemplate.queryForObject(
            "select pg_advisory_xact_lock(hashtextextended(?, 0))",
            Long.class,
            tenantId + ":" + sourceType + ":" + sourceId
        );
    }

    public Optional<PartyReferenceView> findBySourceKey(
        UUID tenantId,
        String sourceType,
        String sourceId
    ) {
        return first(jdbcTemplate.query(
            """
            select id, source_type, source_id, current_display_name, status, created_at, updated_at
              from platform.party_reference
             where tenant_id = ? and source_type = ? and source_id = ?
            """,
            ROW_MAPPER,
            tenantId,
            sourceType,
            sourceId
        ));
    }

    public Optional<PartyReferenceView> findById(UUID tenantId, PartyId partyId) {
        return first(jdbcTemplate.query(
            """
            select id, source_type, source_id, current_display_name, status, created_at, updated_at
              from platform.party_reference
             where tenant_id = ? and id = ?
            """,
            ROW_MAPPER,
            tenantId,
            partyId.value()
        ));
    }

    public PartyReferenceView insert(
        UUID tenantId,
        PartyId partyId,
        String sourceType,
        String sourceId,
        String displayName,
        PartyLifecycleStatus status,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            insert into platform.party_reference (
                id, tenant_id, source_type, source_id, current_display_name,
                status, created_at, updated_at, version
            ) values (?, ?, ?, ?, ?, ?, ?, ?, 0)
            """,
            partyId.value(),
            tenantId,
            sourceType,
            sourceId,
            displayName,
            status.name(),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return findById(tenantId, partyId)
            .orElseThrow(() -> new IllegalStateException("Inserted party reference disappeared"));
    }

    public PartyReferenceView update(
        UUID tenantId,
        PartyId partyId,
        String displayName,
        PartyLifecycleStatus status,
        Instant now
    ) {
        int affected = jdbcTemplate.update(
            """
            update platform.party_reference
               set current_display_name = ?,
                   status = ?,
                   updated_at = ?,
                   version = version + 1
             where tenant_id = ? and id = ?
            """,
            displayName,
            status.name(),
            Timestamp.from(now),
            tenantId,
            partyId.value()
        );
        if (affected != 1) {
            throw new IllegalStateException("Party reference update affected " + affected + " rows");
        }
        return findById(tenantId, partyId)
            .orElseThrow(() -> new IllegalStateException("Updated party reference disappeared"));
    }

    public List<PartyReferenceView> search(
        UUID tenantId,
        String query,
        boolean includeInactive,
        int limit
    ) {
        String normalized = query == null ? "" : query.trim();
        return jdbcTemplate.query(
            """
            select id, source_type, source_id, current_display_name, status, created_at, updated_at
              from platform.party_reference
             where tenant_id = ?
               and (? or status = 'ACTIVE')
               and (
                   ? = ''
                   or current_display_name ilike ('%' || ? || '%')
                   or source_id ilike ('%' || ? || '%')
               )
             order by current_display_name asc, id asc
             limit ?
            """,
            ROW_MAPPER,
            tenantId,
            includeInactive,
            normalized,
            normalized,
            normalized,
            limit
        );
    }

    public long countBySourceKey(UUID tenantId, String sourceType, String sourceId) {
        Long count = jdbcTemplate.queryForObject(
            """
            select count(*) from platform.party_reference
             where tenant_id = ? and source_type = ? and source_id = ?
            """,
            Long.class,
            tenantId,
            sourceType,
            sourceId
        );
        return count == null ? 0 : count;
    }

    private static Optional<PartyReferenceView> first(List<PartyReferenceView> rows) {
        return rows.stream().findFirst();
    }

    private static PartyReferenceView map(ResultSet rs, int rowNumber) throws SQLException {
        return new PartyReferenceView(
            new PartyId(rs.getObject("id", UUID.class)),
            rs.getString("source_type"),
            rs.getString("source_id"),
            rs.getString("current_display_name"),
            PartyLifecycleStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
