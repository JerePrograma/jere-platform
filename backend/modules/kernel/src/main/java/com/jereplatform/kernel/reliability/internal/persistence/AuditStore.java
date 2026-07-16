package com.jereplatform.kernel.reliability.internal.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jereplatform.kernel.reliability.api.AuditEventView;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void insert(
        TenantContext context,
        String actionCode,
        String targetType,
        String targetId,
        String result,
        String failureCode,
        Map<String, String> metadata,
        Instant occurredAt
    ) {
        jdbcTemplate.update(
            """
            insert into platform.audit_event (
                id, tenant_id, actor_identity_id, actor_membership_id,
                action_code, target_type, target_id, result, failure_code,
                correlation_id, metadata, occurred_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """,
            UUID.randomUUID(),
            context.tenantId().value(),
            context.identityId().value(),
            context.membershipId().value(),
            actionCode,
            targetType,
            targetId,
            result,
            failureCode,
            context.correlationId(),
            writeMetadata(metadata),
            Timestamp.from(occurredAt)
        );
    }

    public List<AuditEventView> findLatest(UUID tenantId, int limit) {
        return jdbcTemplate.query(
            """
            select id, actor_identity_id, actor_membership_id, action_code,
                   target_type, target_id, result, failure_code,
                   correlation_id, metadata::text, occurred_at
              from platform.audit_event
             where tenant_id = ?
             order by occurred_at desc, id desc
             limit ?
            """,
            (rs, rowNum) -> new AuditEventView(
                rs.getObject("id", UUID.class),
                rs.getObject("actor_identity_id", UUID.class),
                rs.getObject("actor_membership_id", UUID.class),
                rs.getString("action_code"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("result"),
                rs.getString("failure_code"),
                rs.getObject("correlation_id", UUID.class),
                readMetadata(rs.getString("metadata")),
                rs.getTimestamp("occurred_at").toInstant()
            ),
            tenantId,
            limit
        );
    }

    public long countFailures(UUID tenantId) {
        var count = jdbcTemplate.queryForObject(
            "select count(*) from platform.audit_event where tenant_id = ? and result = 'FAILURE'",
            Long.class,
            tenantId
        );
        return count == null ? 0 : count;
    }

    private String writeMetadata(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Audit metadata cannot be serialized", invalid);
        }
    }

    private Map<String, String> readMetadata(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (Exception invalid) {
            throw new IllegalStateException("Audit metadata cannot be deserialized", invalid);
        }
    }
}
