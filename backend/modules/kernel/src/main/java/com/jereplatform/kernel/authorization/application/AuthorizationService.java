package com.jereplatform.kernel.authorization.application;

import com.jereplatform.kernel.authorization.api.AuthorizationDeniedException;
import com.jereplatform.kernel.authorization.api.AuthorizationSnapshot;
import com.jereplatform.kernel.authorization.api.PlatformPermission;
import com.jereplatform.kernel.authorization.internal.persistence.AuthorizationStore;
import com.jereplatform.kernel.tenancy.api.BranchId;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthorizationService {

    private final AuthorizationStore store;
    private final Clock clock;

    public AuthorizationService(AuthorizationStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public boolean hasPermission(
        TenantContext context,
        PlatformPermission permission,
        BranchId branchId
    ) {
        if (permission.branchScoped()) {
            if (branchId == null || !context.canAccess(branchId)) {
                return false;
            }
        } else if (branchId != null) {
            return false;
        }

        return store.hasPermission(
            context.tenantId().value(),
            context.membershipId().value(),
            permission.code(),
            branchId == null ? null : branchId.value(),
            clock.instant()
        );
    }

    public boolean hasPermission(
        TenantContext context,
        String permissionCode,
        BranchId branchId
    ) {
        return PlatformPermission.fromCode(permissionCode)
            .map(permission -> hasPermission(context, permission, branchId))
            .orElse(false);
    }

    public void require(
        TenantContext context,
        PlatformPermission permission,
        BranchId branchId
    ) {
        if (!hasPermission(context, permission, branchId)) {
            throw new AuthorizationDeniedException();
        }
    }

    public AuthorizationSnapshot snapshot(TenantContext context) {
        var tenantPermissions = new LinkedHashSet<String>();
        var branchPermissions = new LinkedHashMap<UUID, Set<String>>();

        for (var permission : PlatformPermission.values()) {
            if (!permission.branchScoped() && hasPermission(context, permission, null)) {
                tenantPermissions.add(permission.code());
            }
        }

        for (var branchId : context.branchIds()) {
            var effective = new LinkedHashSet<String>();
            for (var permission : PlatformPermission.values()) {
                if (permission.branchScoped() && hasPermission(context, permission, branchId)) {
                    effective.add(permission.code());
                }
            }
            branchPermissions.put(branchId.value(), Set.copyOf(effective));
        }

        return new AuthorizationSnapshot(
            Set.copyOf(tenantPermissions),
            branchPermissions,
            store.activeEntitlements(context.tenantId().value(), clock.instant()),
            store.featureFlags(context.tenantId().value())
        );
    }
}
