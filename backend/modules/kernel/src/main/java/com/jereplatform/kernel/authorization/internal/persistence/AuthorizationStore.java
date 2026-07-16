package com.jereplatform.kernel.authorization.internal.persistence;

import com.jereplatform.kernel.authorization.api.RoleId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthorizationStore {

    private final JdbcTemplate jdbcTemplate;

    public AuthorizationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BaseRoleTemplateRow> findBaseRoleTemplates() {
        return jdbcTemplate.query(
            """
            select system_key, role_code, display_name, template_version
              from platform.base_role_template
             order by system_key
            """,
            (rs, rowNum) -> new BaseRoleTemplateRow(
                rs.getString("system_key"),
                rs.getString("role_code"),
                rs.getString("display_name"),
                rs.getInt("template_version")
            )
        );
    }

    public List<String> findTemplatePermissions(String systemKey) {
        return jdbcTemplate.queryForList(
            """
            select permission_code
              from platform.base_role_template_permission
             where system_key = ?
             order by permission_code
            """,
            String.class,
            systemKey
        );
    }

    public Optional<TenantRoleRow> findManagedRole(UUID tenantId, String systemKey) {
        return jdbcTemplate.query(
            """
            select id, code, display_name, system_key, managed_template_version, active
              from platform.tenant_role
             where tenant_id = ? and system_key = ?
            """,
            rs -> rs.next()
                ? Optional.of(new TenantRoleRow(
                    new RoleId(rs.getObject("id", UUID.class)),
                    rs.getString("code"),
                    rs.getString("display_name"),
                    rs.getString("system_key"),
                    rs.getInt("managed_template_version"),
                    rs.getBoolean("active")
                ))
                : Optional.empty(),
            tenantId,
            systemKey
        );
    }

    public Optional<TenantRoleRow> findRole(UUID tenantId, RoleId roleId) {
        return jdbcTemplate.query(
            """
            select id, code, display_name, system_key, managed_template_version, active
              from platform.tenant_role
             where tenant_id = ? and id = ?
            """,
            rs -> rs.next()
                ? Optional.of(new TenantRoleRow(
                    new RoleId(rs.getObject("id", UUID.class)),
                    rs.getString("code"),
                    rs.getString("display_name"),
                    rs.getString("system_key"),
                    rs.getObject("managed_template_version") == null
                        ? 0
                        : rs.getInt("managed_template_version"),
                    rs.getBoolean("active")
                ))
                : Optional.empty(),
            tenantId,
            roleId.value()
        );
    }

    public RoleId insertManagedRole(
        UUID tenantId,
        BaseRoleTemplateRow template,
        Instant now
    ) {
        var roleId = RoleId.random();
        jdbcTemplate.update(
            """
            insert into platform.tenant_role (
                id, tenant_id, code, display_name, system_key,
                managed_template_version, active, created_at, updated_at, version
            ) values (?, ?, ?, ?, ?, ?, true, ?, ?, 0)
            """,
            roleId.value(),
            tenantId,
            template.roleCode(),
            template.displayName(),
            template.systemKey(),
            template.templateVersion(),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return roleId;
    }

    public void updateManagedRole(
        UUID tenantId,
        RoleId roleId,
        BaseRoleTemplateRow template,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            update platform.tenant_role
               set code = ?,
                   display_name = ?,
                   managed_template_version = ?,
                   active = true,
                   updated_at = ?,
                   version = version + 1
             where tenant_id = ? and id = ? and system_key = ?
            """,
            template.roleCode(),
            template.displayName(),
            template.templateVersion(),
            Timestamp.from(now),
            tenantId,
            roleId.value(),
            template.systemKey()
        );
    }

    public RoleId insertCustomRole(
        UUID tenantId,
        String code,
        String displayName,
        Instant now
    ) {
        var roleId = RoleId.random();
        jdbcTemplate.update(
            """
            insert into platform.tenant_role (
                id, tenant_id, code, display_name, system_key,
                managed_template_version, active, created_at, updated_at, version
            ) values (?, ?, ?, ?, null, null, true, ?, ?, 0)
            """,
            roleId.value(),
            tenantId,
            code,
            displayName,
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return roleId;
    }

    public void replaceRolePermissions(
        UUID tenantId,
        RoleId roleId,
        Collection<String> permissionCodes
    ) {
        jdbcTemplate.update(
            "delete from platform.tenant_role_permission where tenant_id = ? and role_id = ?",
            tenantId,
            roleId.value()
        );
        jdbcTemplate.batchUpdate(
            """
            insert into platform.tenant_role_permission (
                tenant_id, role_id, permission_code
            ) values (?, ?, ?)
            """,
            permissionCodes,
            permissionCodes.size(),
            (statement, permissionCode) -> {
                statement.setObject(1, tenantId);
                statement.setObject(2, roleId.value());
                statement.setString(3, permissionCode);
            }
        );
    }

    public Optional<RoleId> findRoleBySystemKey(UUID tenantId, String systemKey) {
        return jdbcTemplate.query(
            """
            select id
              from platform.tenant_role
             where tenant_id = ? and system_key = ? and active = true
            """,
            rs -> rs.next()
                ? Optional.of(new RoleId(rs.getObject("id", UUID.class)))
                : Optional.empty(),
            tenantId,
            systemKey
        );
    }

    public boolean membershipExists(UUID tenantId, UUID membershipId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            """
            select exists(
                select 1 from platform.membership
                 where tenant_id = ? and id = ? and status = 'ACTIVE'
            )
            """,
            Boolean.class,
            tenantId,
            membershipId
        ));
    }

    public boolean branchGrantExists(UUID tenantId, UUID membershipId, UUID branchId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            """
            select exists(
                select 1 from platform.membership_branch
                 where tenant_id = ? and membership_id = ? and branch_id = ?
            )
            """,
            Boolean.class,
            tenantId,
            membershipId,
            branchId
        ));
    }

    public void assignRole(
        UUID tenantId,
        UUID membershipId,
        RoleId roleId,
        UUID branchId,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            insert into platform.membership_role_assignment (
                id, tenant_id, membership_id, role_id, branch_id, created_at, version
            ) values (?, ?, ?, ?, ?, ?, 0)
            on conflict on constraint uq_membership_role_scope
            do nothing
            """,
            UUID.randomUUID(),
            tenantId,
            membershipId,
            roleId.value(),
            branchId,
            Timestamp.from(now)
        );
    }

    public boolean allPermissionsExist(Collection<String> permissionCodes) {
        if (permissionCodes.isEmpty()) {
            return true;
        }
        var placeholders = permissionCodes.stream().map(ignored -> "?").collect(Collectors.joining(","));
        var count = jdbcTemplate.queryForObject(
            "select count(*) from platform.permission_catalog where active = true and code in ("
                + placeholders + ")",
            Long.class,
            permissionCodes.toArray()
        );
        return count != null && count == permissionCodes.size();
    }

    public boolean moduleExists(String moduleCode) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "select exists(select 1 from platform.module_catalog where code = ? and active = true)",
            Boolean.class,
            moduleCode
        ));
    }

    public void upsertEntitlement(
        UUID tenantId,
        String moduleCode,
        String status,
        String source,
        Instant validFrom,
        Instant validUntil
    ) {
        jdbcTemplate.update(
            """
            insert into platform.tenant_entitlement (
                tenant_id, module_code, status, source, valid_from, valid_until, version
            ) values (?, ?, ?, ?, ?, ?, 0)
            on conflict (tenant_id, module_code)
            do update set status = excluded.status,
                          source = excluded.source,
                          valid_from = excluded.valid_from,
                          valid_until = excluded.valid_until,
                          version = platform.tenant_entitlement.version + 1
            """,
            tenantId,
            moduleCode,
            status,
            source,
            Timestamp.from(validFrom),
            validUntil == null ? null : Timestamp.from(validUntil)
        );
    }

    public void upsertFeatureFlag(UUID tenantId, String featureCode, boolean enabled) {
        jdbcTemplate.update(
            """
            insert into platform.tenant_feature_flag (
                tenant_id, feature_code, enabled, version
            ) values (?, ?, ?, 0)
            on conflict (tenant_id, feature_code)
            do update set enabled = excluded.enabled,
                          version = platform.tenant_feature_flag.version + 1
            """,
            tenantId,
            featureCode,
            enabled
        );
    }

    public boolean featureFlagExists(String featureCode) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "select exists(select 1 from platform.feature_flag_catalog where code = ?)",
            Boolean.class,
            featureCode
        ));
    }

    public boolean hasPermission(
        UUID tenantId,
        UUID membershipId,
        String permissionCode,
        UUID branchId,
        Instant now
    ) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            """
            select exists(
                select 1
                  from platform.membership_role_assignment assignment
                  join platform.tenant_role role
                    on role.tenant_id = assignment.tenant_id
                   and role.id = assignment.role_id
                   and role.active = true
                  join platform.tenant_role_permission role_permission
                    on role_permission.tenant_id = role.tenant_id
                   and role_permission.role_id = role.id
                  join platform.permission_catalog permission
                    on permission.code = role_permission.permission_code
                   and permission.active = true
                  join platform.tenant_entitlement entitlement
                    on entitlement.tenant_id = assignment.tenant_id
                   and entitlement.module_code = permission.module_code
                   and entitlement.status = 'ACTIVE'
                   and entitlement.valid_from <= ?
                   and (entitlement.valid_until is null or entitlement.valid_until > ?)
                 where assignment.tenant_id = ?
                   and assignment.membership_id = ?
                   and permission.code = ?
                   and (
                       (permission.branch_scoped = false and assignment.branch_id is null)
                       or (
                           permission.branch_scoped = true
                           and ?::uuid is not null
                           and (assignment.branch_id is null or assignment.branch_id = ?::uuid)
                       )
                   )
            )
            """,
            Boolean.class,
            Timestamp.from(now),
            Timestamp.from(now),
            tenantId,
            membershipId,
            permissionCode,
            branchId,
            branchId
        ));
    }

    public Set<String> activeEntitlements(UUID tenantId, Instant now) {
        return Set.copyOf(jdbcTemplate.queryForList(
            """
            select module_code
              from platform.tenant_entitlement
             where tenant_id = ?
               and status = 'ACTIVE'
               and valid_from <= ?
               and (valid_until is null or valid_until > ?)
             order by module_code
            """,
            String.class,
            tenantId,
            Timestamp.from(now),
            Timestamp.from(now)
        ));
    }

    public Map<String, Boolean> featureFlags(UUID tenantId) {
        var flags = new LinkedHashMap<String, Boolean>();
        jdbcTemplate.query(
            """
            select catalog.code, coalesce(tenant.enabled, false) as enabled
              from platform.feature_flag_catalog catalog
              left join platform.tenant_feature_flag tenant
                on tenant.feature_code = catalog.code
               and tenant.tenant_id = ?
             order by catalog.code
            """,
            rs -> {
                flags.put(rs.getString("code"), rs.getBoolean("enabled"));
            },
            tenantId
        );
        return Map.copyOf(flags);
    }

    public record BaseRoleTemplateRow(
        String systemKey,
        String roleCode,
        String displayName,
        int templateVersion
    ) {
    }

    public record TenantRoleRow(
        RoleId id,
        String code,
        String displayName,
        String systemKey,
        int managedTemplateVersion,
        boolean active
    ) {

        public boolean managed() {
            return systemKey != null;
        }
    }
}
