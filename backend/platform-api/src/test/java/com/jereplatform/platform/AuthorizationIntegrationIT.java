package com.jereplatform.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.jereplatform.kernel.authorization.api.PlatformPermission;
import com.jereplatform.kernel.authorization.application.AuthorizationAdministrationService;
import com.jereplatform.kernel.authorization.application.AuthorizationService;
import com.jereplatform.kernel.identity.application.CredentialRegistrationService;
import com.jereplatform.kernel.tenancy.api.BranchId;
import com.jereplatform.kernel.tenancy.api.IdentityId;
import com.jereplatform.kernel.tenancy.api.MembershipId;
import com.jereplatform.kernel.tenancy.api.OrganizationId;
import com.jereplatform.kernel.tenancy.api.TenantId;
import com.jereplatform.kernel.tenancy.application.TenantAccessService;
import com.jereplatform.kernel.tenancy.application.TenantProvisioningService;
import com.jereplatform.platform.tenancy.TenantContextFilter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "platform.security.jwt-secret=authorization-integration-secret-0123456789abcdef",
        "platform.security.access-token-lifetime=PT15M",
        "platform.security.refresh-token-lifetime=P30D"
    }
)
class AuthorizationIntegrationIT {

    private static final String PASSWORD = "correct-horse-battery-staple";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantProvisioningService provisioning;

    @Autowired
    private CredentialRegistrationService credentials;

    @Autowired
    private AuthorizationAdministrationService administration;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private TenantAccessService tenantAccessService;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void anonymousIs401AndAuthenticatedWithoutPermissionIs403() {
        var account = createAccount("semantics");
        administration.reconcileBaseRoles(account.tenantId());
        var token = login(account);

        assertThat(tenantProbe(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(tenantProbe(token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void roleInOneTenantGrantsNothingInAnotherTenant() {
        var identity = createIdentity("multi-tenant");
        var first = createAccountForIdentity("first-authz", identity);
        var second = createAccountForIdentity("second-authz", identity);

        administration.reconcileBaseRoles(first.tenantId());
        administration.reconcileBaseRoles(second.tenantId());
        administration.assignBaseRole(
            first.tenantId(), first.membershipId(),
            AuthorizationAdministrationService.OWNER_SYSTEM_KEY, null);

        assertThat(tenantProbe(login(first)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tenantProbe(login(second)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void entitlementAbsenceBlocksModuleIndependentlyOfOwnerRole() {
        var account = createAccount("entitlement");
        administration.bootstrapOwner(account.tenantId(), account.membershipId());
        var token = login(account);

        assertThat(branchProbe(token, account.firstBranch()).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        administration.grantEntitlement(account.tenantId(), "academy", "PLAN", null);
        assertThat(branchProbe(token, account.firstBranch()).getStatusCode())
            .isEqualTo(HttpStatus.OK);

        administration.suspendEntitlement(account.tenantId(), "academy");
        assertThat(branchProbe(token, account.firstBranch()).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void branchScopedRoleGrantsOnlyTheAssignedAccessibleBranch() {
        var account = createAccount("branch-scope");
        administration.reconcileBaseRoles(account.tenantId());
        administration.grantEntitlement(account.tenantId(), "academy", "PLAN", null);
        var roleId = administration.createCustomRole(
            account.tenantId(),
            "branch-reader",
            "Branch Reader",
            java.util.Set.of(PlatformPermission.ACADEMY_STUDENTS_READ)
        );
        administration.assignRole(
            account.tenantId(), account.membershipId(), roleId, account.firstBranch());
        var token = login(account);

        assertThat(branchProbe(token, account.firstBranch()).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(branchProbe(token, account.secondBranch()).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void branchAssignmentCannotGrantTenantWidePermission() {
        var account = createAccount("branch-global");
        administration.reconcileBaseRoles(account.tenantId());
        var roleId = administration.createCustomRole(
            account.tenantId(),
            "branch-session",
            "Branch Session",
            java.util.Set.of(PlatformPermission.PLATFORM_SESSION_READ)
        );
        administration.assignRole(
            account.tenantId(), account.membershipId(), roleId, account.firstBranch());

        assertThat(tenantProbe(login(account)).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void featureFlagDoesNotSubstituteForCommercialEntitlement() {
        var account = createAccount("feature-flag");
        administration.bootstrapOwner(account.tenantId(), account.membershipId());
        administration.setFeatureFlag(
            account.tenantId(), "academy.new-attendance-ui", true);
        var token = login(account);

        assertThat(branchProbe(token, account.firstBranch()).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        var snapshot = authorizationSnapshot(token);
        assertThat(snapshot.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Map<?, ?>) snapshot.getBody().get("featureFlags"))
            .containsEntry("academy.new-attendance-ui", true);
        assertThat((java.util.List<?>) snapshot.getBody().get("entitlements"))
            .doesNotContain("academy");
    }

    @Test
    void baseRoleMatrixIsExplicitAndNotOrdinal() {
        var tenant = createTenant("matrix");
        var owner = addMembership(tenant, "owner-matrix");
        var operator = addMembership(tenant, "operator-matrix");
        var viewer = addMembership(tenant, "viewer-matrix");

        administration.reconcileBaseRoles(tenant.tenantId());
        administration.grantEntitlement(tenant.tenantId(), "academy", "PLAN", null);
        administration.grantEntitlement(tenant.tenantId(), "commerce", "ADDON", null);
        administration.assignBaseRole(
            tenant.tenantId(), owner.membershipId(), "OWNER", null);
        administration.assignBaseRole(
            tenant.tenantId(), operator.membershipId(), "OPERATOR", null);
        administration.assignBaseRole(
            tenant.tenantId(), viewer.membershipId(), "VIEWER", null);

        var ownerContext = tenantAccessService.activate(
            owner.externalSubject(), tenant.tenantCode(), UUID.randomUUID());
        var operatorContext = tenantAccessService.activate(
            operator.externalSubject(), tenant.tenantCode(), UUID.randomUUID());
        var viewerContext = tenantAccessService.activate(
            viewer.externalSubject(), tenant.tenantCode(), UUID.randomUUID());

        assertThat(authorizationService.hasPermission(
            ownerContext, PlatformPermission.ACADEMY_BILLING_MANAGE, null)).isTrue();
        assertThat(authorizationService.hasPermission(
            operatorContext, PlatformPermission.ACADEMY_BILLING_MANAGE, null)).isFalse();
        assertThat(authorizationService.hasPermission(
            operatorContext, PlatformPermission.ACADEMY_ATTENDANCE_MANAGE,
            tenant.firstBranch())).isTrue();
        assertThat(authorizationService.hasPermission(
            viewerContext, PlatformPermission.ACADEMY_STUDENTS_READ,
            tenant.firstBranch())).isTrue();
        assertThat(authorizationService.hasPermission(
            viewerContext, PlatformPermission.ACADEMY_STUDENTS_WRITE,
            tenant.firstBranch())).isFalse();
    }

    @Test
    void reconcilingBaseRolesPreservesCustomRolesAndAssignments() {
        var account = createAccount("custom-preserved");
        administration.reconcileBaseRoles(account.tenantId());
        administration.grantEntitlement(account.tenantId(), "academy", "PLAN", null);
        var custom = administration.createCustomRole(
            account.tenantId(),
            "custom-reader",
            "Custom Reader",
            java.util.Set.of(PlatformPermission.ACADEMY_STUDENTS_READ)
        );
        administration.assignRole(
            account.tenantId(), account.membershipId(), custom, account.firstBranch());

        administration.reconcileBaseRoles(account.tenantId());

        assertThat(branchProbe(login(account), account.firstBranch()).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }

    private TenantFixture createTenant(String prefix) {
        return transactions.execute(status -> {
            var tenantCode = unique(prefix);
            var tenantId = provisioning.createTenant(tenantCode, prefix + " tenant");
            var organizationId = provisioning.createOrganization(
                tenantId, "main", prefix + " organization");
            var firstBranch = provisioning.createBranch(
                tenantId, organizationId, "first", prefix + " first branch");
            var secondBranch = provisioning.createBranch(
                tenantId, organizationId, "second", prefix + " second branch");
            return new TenantFixture(
                tenantId, organizationId, firstBranch, secondBranch, tenantCode);
        });
    }

    private AccountFixture createAccount(String prefix) {
        return addMembership(createTenant(prefix), prefix);
    }

    private IdentityFixture createIdentity(String prefix) {
        return transactions.execute(status -> {
            var subject = unique(prefix) + "@example.com";
            IdentityId identityId = provisioning.registerIdentity(subject);
            credentials.register(identityId, PASSWORD);
            return new IdentityFixture(identityId, subject);
        });
    }

    private AccountFixture createAccountForIdentity(
        String prefix,
        IdentityFixture identity
    ) {
        return addMembership(createTenant(prefix), identity);
    }

    private AccountFixture addMembership(TenantFixture tenant, String identityPrefix) {
        return addMembership(tenant, createIdentity(identityPrefix));
    }

    private AccountFixture addMembership(
        TenantFixture tenant,
        IdentityFixture identity
    ) {
        return transactions.execute(status -> {
            MembershipId membershipId = provisioning.createMembership(
                tenant.tenantId(), identity.identityId(), tenant.organizationId());
            provisioning.grantBranch(
                tenant.tenantId(), membershipId, tenant.firstBranch());
            provisioning.grantBranch(
                tenant.tenantId(), membershipId, tenant.secondBranch());
            return new AccountFixture(
                tenant.tenantId(),
                tenant.organizationId(),
                tenant.firstBranch(),
                tenant.secondBranch(),
                identity.identityId(),
                membershipId,
                tenant.tenantCode(),
                identity.externalSubject()
            );
        });
    }

    private String login(AccountFixture account) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = restTemplate.exchange(
            "/api/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "externalSubject", account.externalSubject(),
                "password", PASSWORD,
                "tenantCode", account.tenantCode()
            ), headers),
            Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private org.springframework.http.ResponseEntity<Map> tenantProbe(String token) {
        return get("/api/authorization/probe/tenant", token, null);
    }

    private org.springframework.http.ResponseEntity<Map> branchProbe(
        String token,
        BranchId branchId
    ) {
        return get("/api/authorization/probe/branch/" + branchId.value(), token, null);
    }

    private org.springframework.http.ResponseEntity<Map> authorizationSnapshot(String token) {
        return get("/api/authorization/current", token, null);
    }

    private org.springframework.http.ResponseEntity<Map> get(
        String path,
        String token,
        String tenantCode
    ) {
        var headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (tenantCode != null) {
            headers.set(TenantContextFilter.TENANT_HEADER, tenantCode);
        }
        return restTemplate.exchange(
            path,
            HttpMethod.GET,
            new HttpEntity<>(null, headers),
            Map.class
        );
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record IdentityFixture(IdentityId identityId, String externalSubject) {
    }

    private record TenantFixture(
        TenantId tenantId,
        OrganizationId organizationId,
        BranchId firstBranch,
        BranchId secondBranch,
        String tenantCode
    ) {
    }

    private record AccountFixture(
        TenantId tenantId,
        OrganizationId organizationId,
        BranchId firstBranch,
        BranchId secondBranch,
        IdentityId identityId,
        MembershipId membershipId,
        String tenantCode,
        String externalSubject
    ) {
    }
}
