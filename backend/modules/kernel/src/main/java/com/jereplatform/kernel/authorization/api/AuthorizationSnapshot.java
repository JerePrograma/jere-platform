package com.jereplatform.kernel.authorization.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record AuthorizationSnapshot(
    Set<String> tenantPermissions,
    Map<UUID, Set<String>> branchPermissions,
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
