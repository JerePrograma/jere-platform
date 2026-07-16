package com.jereplatform.kernel.tenancy.api;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record TenantContext(
    TenantId tenantId,
    IdentityId identityId,
    MembershipId membershipId,
    Set<BranchId> branchIds,
    UUID correlationId
) {

    public TenantContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(identityId, "identityId must not be null");
        Objects.requireNonNull(membershipId, "membershipId must not be null");
        branchIds = Set.copyOf(Objects.requireNonNull(branchIds, "branchIds must not be null"));
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }

    public boolean canAccess(BranchId branchId) {
        return branchIds.contains(branchId);
    }
}
