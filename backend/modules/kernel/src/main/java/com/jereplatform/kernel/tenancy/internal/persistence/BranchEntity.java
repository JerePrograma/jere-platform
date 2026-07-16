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
@Table(schema = "platform", name = "branch")
public class BranchEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LifecycleStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected BranchEntity() {
    }

    public BranchEntity(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        String code,
        String displayName,
        Instant now
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.code = code;
        this.displayName = displayName;
        this.status = LifecycleStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }
}
