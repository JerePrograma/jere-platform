package com.jereplatform.kernel.authorization.api;

import com.jereplatform.kernel.tenancy.api.BranchId;
import java.util.Map;
import java.util.Set;

public record AuthorizationSnapshot(
    Set<String> tenantPermissions,
    Map<BranchId, Set<String>> branchPermissions,
    Set<String> entitlements,
    Map<String, Boolean> featureFlags
) {

    public AuthorizationSnapshot {
        tenantPermissions = Set.copyOf(tenantPermissions);
        branchPermissions = Map.copyOf(branchPermissions);
        entitlements = Set.copyOf(entitlements);
        featureFlags = Map.copyOf(featureFlags);
    }
}
