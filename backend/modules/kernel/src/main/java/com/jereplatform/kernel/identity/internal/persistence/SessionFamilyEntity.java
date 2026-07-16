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
@Table(schema = "platform", name = "session_family")
public class SessionFamilyEntity {

    @Id
    private UUID id;

    @Column(name = "identity_id", nullable = false)
    private UUID identityId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SessionFamilyStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 80)
    private String revocationReason;

    @Version
    @Column(nullable = false)
    private long version;

    protected SessionFamilyEntity() {
    }

    public SessionFamilyEntity(
        UUID id,
        UUID identityId,
        UUID tenantId,
        UUID membershipId,
        long credentialVersion,
        Instant createdAt,
        Instant expiresAt
    ) {
        this.id = id;
        this.identityId = identityId;
        this.tenantId = tenantId;
        this.membershipId = membershipId;
        this.credentialVersion = credentialVersion;
        this.status = SessionFamilyStatus.ACTIVE;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastUsedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdentityId() {
        return identityId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getMembershipId() {
        return membershipId;
    }

    public long getCredentialVersion() {
        return credentialVersion;
    }

    public SessionFamilyStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsableAt(Instant now) {
        return status == SessionFamilyStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public void touch(Instant now) {
        this.lastUsedAt = now;
    }

    public void revoke(Instant now, String reason) {
        if (status == SessionFamilyStatus.REVOKED || status == SessionFamilyStatus.COMPROMISED) {
            return;
        }
        this.status = SessionFamilyStatus.REVOKED;
        this.revokedAt = now;
        this.revocationReason = reason;
        this.lastUsedAt = now;
    }

    public void compromise(Instant now) {
        this.status = SessionFamilyStatus.COMPROMISED;
        this.revokedAt = now;
        this.revocationReason = "REFRESH_REPLAY";
        this.lastUsedAt = now;
    }

    public void expire(Instant now) {
        if (status == SessionFamilyStatus.ACTIVE) {
            this.status = SessionFamilyStatus.EXPIRED;
            this.revokedAt = now;
            this.revocationReason = "EXPIRED";
            this.lastUsedAt = now;
        }
    }
}
