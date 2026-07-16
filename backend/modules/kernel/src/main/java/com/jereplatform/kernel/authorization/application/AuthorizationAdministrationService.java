package com.jereplatform.kernel.authorization.application;

import com.jereplatform.kernel.authorization.api.PlatformPermission;
import com.jereplatform.kernel.authorization.api.RoleId;
import com.jereplatform.kernel.authorization.internal.persistence.AuthorizationStore;
import com.jereplatform.kernel.tenancy.api.BranchId;
import com.jereplatform.kernel.tenancy.api.MembershipId;
import com.jereplatform.kernel.tenancy.api.TenantConflictException;
import com.jereplatform.kernel.tenancy.api.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationAdministrationService {

    public static final String OWNER_SYSTEM_KEY = "OWNER";
    private static final Pattern CODE_PATTERN =
        Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,78}[a-z0-9])?$");

    private final AuthorizationStore store;
    private final Clock clock;

    public AuthorizationAdministrationService(AuthorizationStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public void reconcileBaseRoles(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        var now = clock.instant();
        store.upsertEntitlement(
            tenantId.value(),
            "platform",
            "ACTIVE",
            "SYSTEM",
            now,
            null
        );

        for (var template : store.findBaseRoleTemplates()) {
            var roleId = store.findManagedRole(tenantId.value(), template.systemKey())
                .map(existing -> {
                    store.updateManagedRole(tenantId.value(), existing.id(), template, now);
                    return existing.id();
                })
                .orElseGet(() -> store.insertManagedRole(tenantId.value(), template, now));
            store.replaceRolePermissions(
                tenantId.value(),
                roleId,
                store.findTemplatePermissions(template.systemKey())
            );
        }
    }

    @Transactional
    public void bootstrapOwner(TenantId tenantId, MembershipId membershipId) {
        reconcileBaseRoles(tenantId);
        assignBaseRole(tenantId, membershipId, OWNER_SYSTEM_KEY, null);
    }

    @Transactional
    public RoleId createCustomRole(
        TenantId tenantId,
        String code,
        String displayName,
        Collection<PlatformPermission> permissions
    ) {
        var normalizedCode = normalizeCode(code);
        var normalizedDisplayName = requireDisplayName(displayName);
        var permissionCodes = normalizePermissions(permissions);
        var roleId = store.insertCustomRole(
            tenantId.value(),
            normalizedCode,
            normalizedDisplayName,
            clock.instant()
        );
        store.replaceRolePermissions(tenantId.value(), roleId, permissionCodes);
        return roleId;
    }

    @Transactional
    public void replaceCustomRolePermissions(
        TenantId tenantId,
        RoleId roleId,
        Collection<PlatformPermission> permissions
    ) {
        var role = store.findRole(tenantId.value(), roleId)
            .orElseThrow(() -> new TenantConflictException("Role does not belong to tenant"));
        if (role.managed()) {
            throw new TenantConflictException("Managed base role permissions are reconciled from templates");
        }
        store.replaceRolePermissions(
            tenantId.value(),
            roleId,
            normalizePermissions(permissions)
        );
    }

    @Transactional
    public void assignBaseRole(
        TenantId tenantId,
        MembershipId membershipId,
        String systemKey,
        BranchId branchId
    ) {
        var roleId = store.findRoleBySystemKey(tenantId.value(), normalizeSystemKey(systemKey))
            .orElseThrow(() -> new TenantConflictException("Base role is not reconciled"));
        assignRole(tenantId, membershipId, roleId, branchId);
    }

    @Transactional
    public void assignRole(
        TenantId tenantId,
        MembershipId membershipId,
        RoleId roleId,
        BranchId branchId
    ) {
        if (!store.membershipExists(tenantId.value(), membershipId.value())) {
            throw new TenantConflictException("Membership does not belong to tenant");
        }
        if (store.findRole(tenantId.value(), roleId).filter(role -> role.active()).isEmpty()) {
            throw new TenantConflictException("Role does not belong to tenant");
        }
        if (branchId != null && !store.branchGrantExists(
            tenantId.value(), membershipId.value(), branchId.value())) {
            throw new TenantConflictException("Membership cannot receive a role for an inaccessible branch");
        }
        store.assignRole(
            tenantId.value(),
            membershipId.value(),
            roleId,
            branchId == null ? null : branchId.value(),
            clock.instant()
        );
    }

    @Transactional
    public void grantEntitlement(
        TenantId tenantId,
        String moduleCode,
        String source,
        Instant validUntil
    ) {
        var normalizedModule = normalizeModule(moduleCode);
        if (!store.moduleExists(normalizedModule)) {
            throw new TenantConflictException("Unknown module entitlement");
        }
        var now = clock.instant();
        if (validUntil != null && !validUntil.isAfter(now)) {
            throw new IllegalArgumentException("validUntil must be in the future");
        }
        store.upsertEntitlement(
            tenantId.value(),
            normalizedModule,
            "ACTIVE",
            normalizeSource(source),
            now,
            validUntil
        );
    }

    @Transactional
    public void suspendEntitlement(TenantId tenantId, String moduleCode) {
        var normalizedModule = normalizeModule(moduleCode);
        if (!store.moduleExists(normalizedModule)) {
            throw new TenantConflictException("Unknown module entitlement");
        }
        store.upsertEntitlement(
            tenantId.value(),
            normalizedModule,
            "SUSPENDED",
            "MANUAL",
            clock.instant(),
            null
        );
    }

    @Transactional
    public void setFeatureFlag(TenantId tenantId, String featureCode, boolean enabled) {
        if (featureCode == null || featureCode.isBlank() || !store.featureFlagExists(featureCode.trim())) {
            throw new TenantConflictException("Unknown feature flag");
        }
        store.upsertFeatureFlag(tenantId.value(), featureCode.trim(), enabled);
    }

    private static Set<String> normalizePermissions(Collection<PlatformPermission> permissions) {
        Objects.requireNonNull(permissions, "permissions must not be null");
        return permissions.stream()
            .map(PlatformPermission::code)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeCode(String value) {
        var normalized = requireText(value, "code").toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid role code");
        }
        return normalized;
    }

    private static String requireDisplayName(String value) {
        var normalized = requireText(value, "displayName");
        if (normalized.length() > 160) {
            throw new IllegalArgumentException("displayName must be at most 160 characters");
        }
        return normalized;
    }

    private static String normalizeSystemKey(String value) {
        return requireText(value, "systemKey").toUpperCase(Locale.ROOT);
    }

    private static String normalizeModule(String value) {
        return requireText(value, "moduleCode").toLowerCase(Locale.ROOT);
    }

    private static String normalizeSource(String value) {
        var normalized = requireText(value, "source").toUpperCase(Locale.ROOT);
        if (!Set.of("SYSTEM", "PLAN", "ADDON", "MANUAL").contains(normalized)) {
            throw new IllegalArgumentException("Invalid entitlement source");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
