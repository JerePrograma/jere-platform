package com.jereplatform.kernel.tenancy.internal.persistence;

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
@Table(schema = "platform", name = "membership")
public class MembershipEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "identity_id", nullable = false)
    private UUID identityId;

    @Column(name = "default_organization_id")
    private UUID defaultOrganizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LifecycleStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected MembershipEntity() {
    }

    public MembershipEntity(
        UUID id,
        UUID tenantId,
        UUID identityId,
        UUID defaultOrganizationId,
        Instant now
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.identityId = identityId;
        this.defaultOrganizationId = defaultOrganizationId;
        this.status = LifecycleStatus.ACTIVE;
        this.joinedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getIdentityId() {
        return identityId;
    }

    public LifecycleStatus getStatus() {
        return status;
    }

    public void revoke(Instant now) {
        if (status == LifecycleStatus.REVOKED) {
            return;
        }
        status = LifecycleStatus.REVOKED;
        revokedAt = now;
    }
}
