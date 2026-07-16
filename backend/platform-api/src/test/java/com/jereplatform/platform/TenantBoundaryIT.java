package com.jereplatform.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jereplatform.kernel.tenancy.api.BranchId;
import com.jereplatform.kernel.tenancy.api.IdentityId;
import com.jereplatform.kernel.tenancy.api.MembershipId;
import com.jereplatform.kernel.tenancy.api.OrganizationId;
import com.jereplatform.kernel.tenancy.api.TenantAccessDeniedException;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import com.jereplatform.kernel.tenancy.api.TenantContextHolder;
import com.jereplatform.kernel.tenancy.api.TenantId;
import com.jereplatform.kernel.tenancy.application.BranchDirectory;
import com.jereplatform.kernel.tenancy.application.TenantAccessService;
import com.jereplatform.kernel.tenancy.application.TenantProvisioningService;
import com.jereplatform.platform.tenancy.TenantContextFilter;
import jakarta.persistence.EntityManager;
import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Transactional
@SpringBootTest
class TenantBoundaryIT {

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
    private TenantProvisioningService provisioning;

    @Autowired
    private TenantAccessService accessService;

    @Autowired
    private BranchDirectory branchDirectory;

    @Autowired
    private TenantContextFilter tenantContextFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void allowsEquivalentBusinessCodesAcrossDifferentTenants() {
        var first = createTenant("tenant-one", "Tenant One");
        var second = createTenant("tenant-two", "Tenant Two");

        assertThat(first.organizationId()).isNotEqualTo(second.organizationId());
        assertThat(first.branchId()).isNotEqualTo(second.branchId());
    }

    @Test
    void deniesChangingTheRequestedTenantWithoutMembership() {
        var authorized = createTenant("authorized", "Authorized Tenant");
        createTenant("forbidden", "Forbidden Tenant");
        var identity = provisioning.registerIdentity("person@example.com");
        provisioning.createMembership(
            authorized.tenantId(),
            identity,
            authorized.organizationId()
        );

        var context = accessService.activate(
            "person@example.com",
            "authorized",
            UUID.randomUUID()
        );

        assertThat(context.tenantId()).isEqualTo(authorized.tenantId());
        assertThatThrownBy(() -> accessService.activate(
            "person@example.com",
            "forbidden",
            UUID.randomUUID()
        )).isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void branchDirectoryUsesOnlyTheResolvedTenantContext() {
        var first = createTenant("first", "First Tenant");
        var second = createTenant("second", "Second Tenant");
        var identity = provisioning.registerIdentity("multi@example.com");

        var firstMembership = provisioning.createMembership(
            first.tenantId(), identity, first.organizationId());
        var secondMembership = provisioning.createMembership(
            second.tenantId(), identity, second.organizationId());
        provisioning.grantBranch(first.tenantId(), firstMembership, first.branchId());
        provisioning.grantBranch(second.tenantId(), secondMembership, second.branchId());

        var firstContext = accessService.activate("multi@example.com", "first", UUID.randomUUID());
        var secondContext = accessService.activate("multi@example.com", "second", UUID.randomUUID());

        assertThat(branchDirectory.listAccessible(firstContext))
            .extracting(branch -> branch.id())
            .containsExactly(first.branchId());
        assertThat(branchDirectory.listAccessible(secondContext))
            .extracting(branch -> branch.id())
            .containsExactly(second.branchId());
    }

    @Test
    void membershipRevocationPreventsSubsequentActivation() {
        var tenant = createTenant("revocable", "Revocable Tenant");
        var identity = provisioning.registerIdentity("revoked@example.com");
        var membership = provisioning.createMembership(
            tenant.tenantId(), identity, tenant.organizationId());

        assertThat(accessService.activate(
            "revoked@example.com", "revocable", UUID.randomUUID()))
            .isNotNull();

        provisioning.revokeMembership(tenant.tenantId(), membership);
        entityManager.flush();

        assertThatThrownBy(() -> accessService.activate(
            "revoked@example.com", "revocable", UUID.randomUUID()))
            .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void httpFilterUsesPrincipalMembershipAndClearsThreadContext() throws Exception {
        var tenant = createTenant("http-tenant", "HTTP Tenant");
        createTenant("other-http", "Other HTTP Tenant");
        var identity = provisioning.registerIdentity("principal@example.com");
        var membership = provisioning.createMembership(
            tenant.tenantId(), identity, tenant.organizationId());
        provisioning.grantBranch(tenant.tenantId(), membership, tenant.branchId());
        entityManager.flush();

        var request = apiRequest("http-tenant", "principal@example.com");
        var response = new MockHttpServletResponse();
        var captured = new AtomicReference<TenantContext>();

        tenantContextFilter.doFilter(
            request,
            response,
            (servletRequest, servletResponse) ->
                captured.set(TenantContextHolder.requireCurrent())
        );

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(captured.get().tenantId()).isEqualTo(tenant.tenantId());
        assertThat(captured.get().canAccess(tenant.branchId())).isTrue();
        assertThat(TenantContextHolder.current()).isEmpty();

        var forbiddenRequest = apiRequest("other-http", "principal@example.com");
        var forbiddenResponse = new MockHttpServletResponse();
        var invoked = new AtomicBoolean(false);

        tenantContextFilter.doFilter(
            forbiddenRequest,
            forbiddenResponse,
            (servletRequest, servletResponse) -> invoked.set(true)
        );

        assertThat(invoked).isFalse();
        assertThat(forbiddenResponse.getStatus()).isEqualTo(403);
        assertThat(TenantContextHolder.current()).isEmpty();
    }

    @Test
    void databaseRejectsCrossTenantOrganizationOwnership() {
        var first = createTenant("database-one", "Database One");
        var second = createTenant("database-two", "Database Two");
        entityManager.flush();

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            insert into platform.branch (
                id, tenant_id, organization_id, code, display_name,
                status, created_at, updated_at, version
            ) values (?, ?, ?, ?, ?, 'ACTIVE', current_timestamp, current_timestamp, 0)
            """,
            UUID.randomUUID(),
            first.tenantId().value(),
            second.organizationId().value(),
            "invalid-cross-tenant",
            "Invalid Cross Tenant"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private TenantFixture createTenant(String code, String displayName) {
        TenantId tenantId = provisioning.createTenant(code, displayName);
        OrganizationId organizationId = provisioning.createOrganization(
            tenantId, "main", displayName + " Organization");
        BranchId branchId = provisioning.createBranch(
            tenantId, organizationId, "main", displayName + " Main Branch");
        return new TenantFixture(tenantId, organizationId, branchId);
    }

    private static MockHttpServletRequest apiRequest(String tenantCode, String subject) {
        var request = new MockHttpServletRequest("GET", "/api/branches");
        Principal principal = () -> subject;
        request.setUserPrincipal(principal);
        request.addHeader(TenantContextFilter.TENANT_HEADER, tenantCode);
        request.addHeader(TenantContextFilter.CORRELATION_HEADER, UUID.randomUUID().toString());
        return request;
    }

    private record TenantFixture(
        TenantId tenantId,
        OrganizationId organizationId,
        BranchId branchId
    ) {
    }
}
