package com.jereplatform.kernel.reliability.internal.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyStore {

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureRecord(
        UUID tenantId,
        String operationCode,
        String keyHash,
        String requestHash,
        UUID ownerToken,
        Instant lockedUntil,
        Instant createdAt,
        Instant expiresAt
    ) {
        jdbcTemplate.update(
            """
            insert into platform.idempotency_record (
                tenant_id, operation_code, key_hash, request_hash, status,
                owner_token, locked_until, created_at, updated_at, expires_at, version
            ) values (?, ?, ?, ?, 'IN_PROGRESS', ?, ?, ?, ?, ?, 0)
            on conflict (tenant_id, operation_code, key_hash) do nothing
            """,
            tenantId,
            operationCode,
            keyHash,
            requestHash,
            ownerToken,
            Timestamp.from(lockedUntil),
            Timestamp.from(createdAt),
            Timestamp.from(createdAt),
            Timestamp.from(expiresAt)
        );
    }

    public Optional<IdempotencyRow> lockRecord(
        UUID tenantId,
        String operationCode,
        String keyHash
    ) {
        return jdbcTemplate.query(
            """
            select request_hash, status, owner_token, locked_until,
                   response_type, response_json::text, expires_at
              from platform.idempotency_record
             where tenant_id = ? and operation_code = ? and key_hash = ?
             for update
            """,
            rs -> rs.next()
                ? Optional.of(new IdempotencyRow(
                    rs.getString("request_hash"),
                    rs.getString("status"),
                    rs.getObject("owner_token", UUID.class),
                    rs.getTimestamp("locked_until").toInstant(),
                    rs.getString("response_type"),
                    rs.getString("response_json"),
                    rs.getTimestamp("expires_at").toInstant()
                ))
                : Optional.empty(),
            tenantId,
            operationCode,
            keyHash
        );
    }

    public void takeOwnership(
        UUID tenantId,
        String operationCode,
        String keyHash,
        UUID ownerToken,
        Instant lockedUntil,
        Instant updatedAt
    ) {
        jdbcTemplate.update(
            """
            update platform.idempotency_record
               set owner_token = ?,
                   locked_until = ?,
                   updated_at = ?,
                   version = version + 1
             where tenant_id = ? and operation_code = ? and key_hash = ?
               and status = 'IN_PROGRESS'
            """,
            ownerToken,
            Timestamp.from(lockedUntil),
            Timestamp.from(updatedAt),
            tenantId,
            operationCode,
            keyHash
        );
    }

    public void complete(
        UUID tenantId,
        String operationCode,
        String keyHash,
        UUID ownerToken,
        String responseType,
        String responseJson,
        Instant completedAt
    ) {
        var updated = jdbcTemplate.update(
            """
            update platform.idempotency_record
               set status = 'COMPLETED',
                   response_type = ?,
                   response_json = ?::jsonb,
                   completed_at = ?,
                   updated_at = ?,
                   locked_until = ?,
                   version = version + 1
             where tenant_id = ? and operation_code = ? and key_hash = ?
               and status = 'IN_PROGRESS' and owner_token = ?
            """,
            responseType,
            responseJson,
            Timestamp.from(completedAt),
            Timestamp.from(completedAt),
            Timestamp.from(completedAt),
            tenantId,
            operationCode,
            keyHash,
            ownerToken
        );
        if (updated != 1) {
            throw new IllegalStateException("Idempotency record ownership was lost");
        }
    }

    public int deleteExpiredCompleted(Instant now, int limit) {
        return jdbcTemplate.update(
            """
            delete from platform.idempotency_record
             where ctid in (
                select ctid
                  from platform.idempotency_record
                 where status = 'COMPLETED' and expires_at < ?
                 order by expires_at
                 limit ?
             )
            """,
            Timestamp.from(now),
            limit
        );
    }

    public long countInProgress(UUID tenantId) {
        var count = jdbcTemplate.queryForObject(
            """
            select count(*) from platform.idempotency_record
             where tenant_id = ? and status = 'IN_PROGRESS'
            """,
            Long.class,
            tenantId
        );
        return count == null ? 0 : count;
    }

    public record IdempotencyRow(
        String requestHash,
        String status,
        UUID ownerToken,
        Instant lockedUntil,
        String responseType,
        String responseJson,
        Instant expiresAt
    ) {
    }
}
