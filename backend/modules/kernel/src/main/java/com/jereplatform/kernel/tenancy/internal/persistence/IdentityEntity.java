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
@Table(schema = "platform", name = "identity")
public class IdentityEntity {

    @Id
    private UUID id;

    @Column(name = "external_subject", nullable = false, length = 190)
    private String externalSubject;

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

    protected IdentityEntity() {
    }

    public IdentityEntity(UUID id, String externalSubject, Instant now) {
        this.id = id;
        this.externalSubject = externalSubject;
        this.status = LifecycleStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public LifecycleStatus getStatus() {
        return status;
    }
}
