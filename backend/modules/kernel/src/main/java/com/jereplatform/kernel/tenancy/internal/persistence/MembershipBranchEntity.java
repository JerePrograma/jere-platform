package com.jereplatform.kernel.tenancy.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "platform", name = "membership_branch")
public class MembershipBranchEntity {

    @EmbeddedId
    private MembershipBranchId id;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected MembershipBranchEntity() {
    }

    public MembershipBranchEntity(MembershipBranchId id, Instant grantedAt) {
        this.id = id;
        this.grantedAt = grantedAt;
    }

    public MembershipBranchId getId() {
        return id;
    }
}
