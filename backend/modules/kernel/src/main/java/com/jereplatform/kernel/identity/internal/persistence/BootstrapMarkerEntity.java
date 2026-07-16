package com.jereplatform.kernel.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "platform", name = "bootstrap_marker")
public class BootstrapMarkerEntity {

    @Id
    private Short id;

    @Column(name = "initialized_at", nullable = false)
    private Instant initializedAt;

    @Column(name = "identity_bootstrapped_at")
    private Instant identityBootstrappedAt;

    protected BootstrapMarkerEntity() {
    }

    public boolean isIdentityBootstrapped() {
        return identityBootstrappedAt != null;
    }

    public void claimIdentityBootstrap(Instant now) {
        if (identityBootstrappedAt != null) {
            throw new IllegalStateException("Platform identity bootstrap is already completed");
        }
        identityBootstrappedAt = now;
    }
}
