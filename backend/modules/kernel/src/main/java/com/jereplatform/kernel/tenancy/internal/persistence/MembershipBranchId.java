package com.jereplatform.kernel.tenancy.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MembershipBranchId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    protected MembershipBranchId() {
    }

    public MembershipBranchId(UUID tenantId, UUID membershipId, UUID branchId) {
        this.tenantId = tenantId;
        this.membershipId = membershipId;
        this.branchId = branchId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getMembershipId() {
        return membershipId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MembershipBranchId that)) {
            return false;
        }
        return Objects.equals(tenantId, that.tenantId)
            && Objects.equals(membershipId, that.membershipId)
            && Objects.equals(branchId, that.branchId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, membershipId, branchId);
    }
}
