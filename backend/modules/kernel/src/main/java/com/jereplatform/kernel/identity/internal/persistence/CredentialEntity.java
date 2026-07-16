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
@Table(schema = "platform", name = "credential")
public class CredentialEntity {

    @Id
    @Column(name = "identity_id", nullable = false)
    private UUID identityId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CredentialStatus status;

    @Column(name = "invalidation_version", nullable = false)
    private long invalidationVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected CredentialEntity() {
    }

    public CredentialEntity(UUID identityId, String passwordHash, Instant now) {
        this.identityId = identityId;
        this.passwordHash = passwordHash;
        this.status = CredentialStatus.ACTIVE;
        this.invalidationVersion = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getIdentityId() {
        return identityId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public CredentialStatus getStatus() {
        return status;
    }

    public long getInvalidationVersion() {
        return invalidationVersion;
    }

    public void replacePassword(String encodedPassword, Instant now) {
        this.passwordHash = encodedPassword;
        this.invalidationVersion++;
        this.updatedAt = now;
    }

    public void disable(Instant now) {
        this.status = CredentialStatus.DISABLED;
        this.invalidationVersion++;
        this.updatedAt = now;
    }
}
