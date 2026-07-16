package com.jereplatform.kernel.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "platform", name = "refresh_token")
public class RefreshTokenEntity {

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private long sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefreshTokenStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Version
    @Column(nullable = false)
    private long version;

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(
        UUID id,
        UUID familyId,
        String tokenHash,
        long sequence,
        Instant issuedAt,
        Instant expiresAt
    ) {
        this.id = id;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.sequence = sequence;
        this.status = RefreshTokenStatus.ACTIVE;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public long getSequence() {
        return sequence;
    }

    public RefreshTokenStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void consume(Instant now, UUID replacementId) {
        this.status = RefreshTokenStatus.CONSUMED;
        this.consumedAt = now;
        this.replacedById = replacementId;
    }

    public void revoke() {
        if (status == RefreshTokenStatus.ACTIVE) {
            status = RefreshTokenStatus.REVOKED;
        }
    }
}
