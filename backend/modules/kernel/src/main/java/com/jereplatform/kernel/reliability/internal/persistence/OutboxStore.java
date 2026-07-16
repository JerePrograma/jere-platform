package com.jereplatform.kernel.reliability.internal.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jereplatform.kernel.reliability.api.OutboxFailureView;
import com.jereplatform.kernel.reliability.api.OutboxMessage;
import com.jereplatform.kernel.tenancy.api.TenantId;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public UUID insert(
        UUID tenantId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payloadJson,
        Map<String, String> headers,
        int maxAttempts,
        Instant now
    ) {
        var id = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into platform.outbox_event (
                id, tenant_id, aggregate_type, aggregate_id, event_type,
                payload, headers, status, attempts, max_attempts,
                available_at, created_at, version
            ) values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, 'PENDING', 0, ?, ?, ?, 0)
            """,
            id,
            tenantId,
            aggregateType,
            aggregateId,
            eventType,
            payloadJson,
            writeHeaders(headers),
            maxAttempts,
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return id;
    }

    public int markExhaustedExpiredClaimsDead(Instant now) {
        return jdbcTemplate.update(
            """
            update platform.outbox_event
               set status = 'DEAD',
                   claim_token = null,
                   claimed_at = null,
                   claim_expires_at = null,
                   last_error_code = coalesce(last_error_code, 'CLAIM_LEASE_EXPIRED'),
                   version = version + 1
             where status = 'CLAIMED'
               and claim_expires_at <= ?
               and attempts >= max_attempts
            """,
            Timestamp.from(now)
        );
    }

    public List<OutboxMessage> claimBatch(
        int limit,
        Duration lease,
        Instant now
    ) {
        var claimToken = UUID.randomUUID();
        var claimExpiresAt = now.plus(lease);
        return jdbcTemplate.query(
            """
            with candidates as (
                select id
                  from platform.outbox_event
                 where attempts < max_attempts
                   and (
                       (status in ('PENDING', 'RETRY') and available_at <= ?)
                       or (status = 'CLAIMED' and claim_expires_at <= ?)
                   )
                 order by available_at, created_at, id
                 for update skip locked
                 limit ?
            )
            update platform.outbox_event event
               set status = 'CLAIMED',
                   attempts = event.attempts + 1,
                   claim_token = ?,
                   claimed_at = ?,
                   claim_expires_at = ?,
                   last_error_code = null,
                   version = event.version + 1
              from candidates
             where event.id = candidates.id
            returning event.id, event.tenant_id, event.aggregate_type,
                      event.aggregate_id, event.event_type, event.payload::text,
                      event.headers::text, event.attempts, event.max_attempts,
                      event.claim_token, event.created_at
            """,
            (rs, rowNum) -> new OutboxMessage(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("payload"),
                readHeaders(rs.getString("headers")),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                rs.getObject("claim_token", UUID.class),
                rs.getTimestamp("created_at").toInstant()
            ),
            Timestamp.from(now),
            Timestamp.from(now),
            limit,
            claimToken,
            Timestamp.from(now),
            Timestamp.from(claimExpiresAt)
        );
    }

    public boolean markDispatched(OutboxMessage message, Instant now) {
        return jdbcTemplate.update(
            """
            update platform.outbox_event
               set status = 'DISPATCHED',
                   dispatched_at = ?,
                   claim_token = null,
                   claimed_at = null,
                   claim_expires_at = null,
                   last_error_code = null,
                   version = version + 1
             where id = ? and tenant_id = ?
               and status = 'CLAIMED' and claim_token = ?
            """,
            Timestamp.from(now),
            message.id(),
            message.tenantId().value(),
            message.claimToken()
        ) == 1;
    }

    public boolean markFailed(
        OutboxMessage message,
        String failureCode,
        Instant nextAvailableAt
    ) {
        var terminal = message.attempt() >= message.maxAttempts();
        return jdbcTemplate.update(
            """
            update platform.outbox_event
               set status = ?,
                   available_at = ?,
                   claim_token = null,
                   claimed_at = null,
                   claim_expires_at = null,
                   last_error_code = ?,
                   version = version + 1
             where id = ? and tenant_id = ?
               and status = 'CLAIMED' and claim_token = ?
            """,
            terminal ? "DEAD" : "RETRY",
            Timestamp.from(nextAvailableAt),
            failureCode,
            message.id(),
            message.tenantId().value(),
            message.claimToken()
        ) == 1;
    }

    public boolean requeueDead(UUID tenantId, UUID eventId, Instant now) {
        return jdbcTemplate.update(
            """
            update platform.outbox_event
               set status = 'RETRY',
                   attempts = 0,
                   available_at = ?,
                   claim_token = null,
                   claimed_at = null,
                   claim_expires_at = null,
                   last_error_code = null,
                   dispatched_at = null,
                   version = version + 1
             where tenant_id = ? and id = ? and status = 'DEAD'
            """,
            Timestamp.from(now),
            tenantId,
            eventId
        ) == 1;
    }

    public List<OutboxFailureView> findDead(UUID tenantId, int limit) {
        return jdbcTemplate.query(
            """
            select id, aggregate_type, aggregate_id, event_type,
                   attempts, max_attempts, last_error_code, created_at
              from platform.outbox_event
             where tenant_id = ? and status = 'DEAD'
             order by created_at desc, id desc
             limit ?
            """,
            (rs, rowNum) -> new OutboxFailureView(
                rs.getObject("id", UUID.class),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                rs.getString("last_error_code"),
                rs.getTimestamp("created_at").toInstant()
            ),
            tenantId,
            limit
        );
    }

    public Map<String, Long> countByStatus(UUID tenantId) {
        var result = new LinkedHashMap<String, Long>();
        jdbcTemplate.query(
            """
            select status, count(*) as total
              from platform.outbox_event
             where tenant_id = ?
             group by status
             order by status
            """,
            rs -> {
                result.put(rs.getString("status"), rs.getLong("total"));
            },
            tenantId
        );
        return Map.copyOf(result);
    }

    public Optional<Instant> oldestDeliverable(UUID tenantId) {
        return jdbcTemplate.query(
            """
            select min(created_at) as oldest
              from platform.outbox_event
             where tenant_id = ? and status in ('PENDING', 'RETRY', 'CLAIMED')
            """,
            rs -> {
                if (!rs.next() || rs.getTimestamp("oldest") == null) {
                    return Optional.empty();
                }
                return Optional.of(rs.getTimestamp("oldest").toInstant());
            },
            tenantId
        );
    }

    public int deleteOldDispatched(Instant olderThan, int limit) {
        return jdbcTemplate.update(
            """
            delete from platform.outbox_event
             where ctid in (
                select ctid
                  from platform.outbox_event
                 where status = 'DISPATCHED' and dispatched_at < ?
                 order by dispatched_at
                 limit ?
             )
            """,
            Timestamp.from(olderThan),
            limit
        );
    }

    private String writeHeaders(Map<String, String> headers) {
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Outbox headers cannot be serialized", invalid);
        }
    }

    private Map<String, String> readHeaders(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (Exception invalid) {
            throw new IllegalStateException("Outbox headers cannot be deserialized", invalid);
        }
    }
}
