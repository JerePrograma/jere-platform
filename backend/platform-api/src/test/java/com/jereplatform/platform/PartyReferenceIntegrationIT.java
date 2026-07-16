package com.jereplatform.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jereplatform.commercial.parties.api.PartyInactiveException;
import com.jereplatform.commercial.parties.api.PartyLifecycleStatus;
import com.jereplatform.commercial.parties.api.PartyMutationType;
import com.jereplatform.commercial.parties.api.PartyReferenceNotFoundException;
import com.jereplatform.commercial.parties.api.PartySourceCandidate;
import com.jereplatform.commercial.parties.api.PartySourceRecord;
import com.jereplatform.commercial.parties.application.PartyReconciliationService;
import com.jereplatform.commercial.parties.application.PartyReferenceService;
import com.jereplatform.kernel.authorization.application.AuthorizationAdministrationService;
import com.jereplatform.kernel.identity.application.CredentialRegistrationService;
import com.jereplatform.kernel.reliability.api.IdempotencyConflictException;
import com.jereplatform.kernel.tenancy.api.IdentityId;
import com.jereplatform.kernel.tenancy.api.MembershipId;
import com.jereplatform.kernel.tenancy.api.OrganizationId;
import com.jereplatform.kernel.tenancy.api.TenantId;
import com.jereplatform.kernel.tenancy.application.TenantAccessService;
import com.jereplatform.kernel.tenancy.application.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "platform.security.jwt-secret=party-reference-integration-secret-0123456789abcdef",
        "platform.security.access-token-lifetime=PT15M",
        "platform.security.refresh-token-lifetime=P30D"
    }
)
class PartyReferenceIntegrationIT {

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
    private TenantProvisioningService provisioning;

    @Autowired
    private CredentialRegistrationService credentials;

    @Autowired
    private TenantAccessService tenantAccessService;

    @Autowired
    private AuthorizationAdministrationService authorizationAdministration;

    @Autowired
    private PartyReferenceService parties;

    @Autowired
    private PartyReconciliationService reconciliation;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void sameSourceIdentifierIsIsolatedAcrossTenants() {
        var first = createAccount("tenant-one");
        var second = createAccount("tenant-two");
        var sourceId = "shared-source-42";

        var firstResult = parties.importRecord(
            context(first),
            active("GESTUDIO_STUDENT", sourceId, "First Tenant Student"),
            unique("import")
        );
        var secondResult = parties.importRecord(
            context(second),
            active("GESTUDIO_STUDENT", sourceId, "Second Tenant Student"),
            unique("import")
        );

        assertThat(firstResult.value().party().id())
            .isNotEqualTo(secondResult.value().party().id());
        assertThat(parties.findBySource(context(first), "GESTUDIO_STUDENT", sourceId)
            .currentDisplayName()).isEqualTo("First Tenant Student");
        assertThat(parties.findBySource(context(second), "GESTUDIO_STUDENT", sourceId)
            .currentDisplayName()).isEqualTo("Second Tenant Student");
        assertThatThrownBy(() -> parties.find(
            context(second),
            firstResult.value().party().id()
        )).isInstanceOf(PartyReferenceNotFoundException.class);
    }

    @Test
    void equivalentRetryReplaysAndChangedContentConflicts() {
        var account = createAccount("replay");
        var context = context(account);
        var key = unique("party-key");
        var record = active("SCALARIS_THIRD_PARTY", "party-7", "Maderas del Sur SRL");

        var created = parties.importRecord(context, record, key);
        var replay = parties.importRecord(context, record, key);

        assertThat(created.replayed()).isFalse();
        assertThat(created.value().mutation()).isEqualTo(PartyMutationType.CREATED);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.value().party().id()).isEqualTo(created.value().party().id());

        assertThatThrownBy(() -> parties.importRecord(
            context,
            active("SCALARIS_THIRD_PARTY", "party-7", "Different Content"),
            key
        )).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void concurrentImportsWithDifferentKeysCreateOneMapping() throws Exception {
        var account = createAccount("concurrent");
        var context = context(account);
        var record = active("GESTUDIO_STUDENT", "student-99", "Grace Hopper");
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return parties.importRecord(context, record, unique("first"));
            });
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return parties.importRecord(context, record, unique("second"));
            });
            start.countDown();

            var results = List.of(
                first.get(15, TimeUnit.SECONDS),
                second.get(15, TimeUnit.SECONDS)
            );

            assertThat(results)
                .extracting(result -> result.value().mutation())
                .containsExactlyInAnyOrder(PartyMutationType.CREATED, PartyMutationType.UNCHANGED);
        }

        Long count = jdbcTemplate.queryForObject(
            """
            select count(*) from platform.party_reference
             where tenant_id = ? and source_type = ? and source_id = ?
            """,
            Long.class,
            account.tenantId().value(),
            "GESTUDIO_STUDENT",
            "student-99"
        );
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void renameAndDeactivationDoNotRewriteHistoricalSnapshot() {
        var account = createAccount("snapshot");
        var context = context(account);
        var created = parties.importRecord(
            context,
            active("GESTUDIO_STUDENT", "student-1", "Ada Lovelace"),
            unique("create")
        ).value().party();
        var issued = parties.snapshotForNewOperation(context, created.id());

        var updated = parties.importRecord(
            context,
            new PartySourceRecord(
                "GESTUDIO_STUDENT",
                "student-1",
                "Ada Byron",
                PartyLifecycleStatus.INACTIVE
            ),
            unique("update")
        ).value().party();

        assertThat(updated.currentDisplayName()).isEqualTo("Ada Byron");
        assertThat(updated.active()).isFalse();
        assertThat(issued.displayNameSnapshot()).isEqualTo("Ada Lovelace");
        assertThatThrownBy(() -> parties.requireActive(context, created.id()))
            .isInstanceOf(PartyInactiveException.class);
        assertThat(parties.search(context, "Ada", false, 25)).isEmpty();
        assertThat(parties.search(context, "Ada", true, 25)).hasSize(1);
    }

    @Test
    void mappingChangeCommitsAuditAndOutboxAtomically() {
        var account = createAccount("reliable");
        var imported = parties.importRecord(
            context(account),
            active("SCALARIS_THIRD_PARTY", "tp-atomic", "Atomic Party"),
            unique("atomic")
        );

        Integer auditCount = jdbcTemplate.queryForObject(
            """
            select count(*) from platform.audit_event
             where tenant_id = ? and action_code = 'PARTY_REFERENCE_IMPORT'
               and result = 'SUCCESS'
            """,
            Integer.class,
            account.tenantId().value()
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
            """
            select count(*) from platform.outbox_event
             where tenant_id = ? and aggregate_type = 'PARTY_REFERENCE'
               and aggregate_id = ?
            """,
            Integer.class,
            account.tenantId().value(),
            imported.value().party().id().value().toString()
        );

        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);

        parties.importRecord(
            context(account),
            active("SCALARIS_THIRD_PARTY", "tp-atomic", "Atomic Party"),
            unique("unchanged")
        );
        Integer unchangedOutboxCount = jdbcTemplate.queryForObject(
            """
            select count(*) from platform.outbox_event
             where tenant_id = ? and aggregate_type = 'PARTY_REFERENCE'
               and aggregate_id = ?
            """,
            Integer.class,
            account.tenantId().value(),
            imported.value().party().id().value().toString()
        );
        assertThat(unchangedOutboxCount).isEqualTo(1);
    }

    @Test
    void reconciliationReportsInvalidDuplicatesAndChangesWithoutWriting() {
        var account = createAccount("reconcile");
        var context = context(account);
        parties.importRecord(
            context,
            active("GESTUDIO_STUDENT", "known", "Original Name"),
            unique("known")
        );

        var report = reconciliation.analyze(context, List.of(
            new PartySourceCandidate("GESTUDIO_STUDENT", "known", "New Name", true),
            new PartySourceCandidate("SCALARIS_THIRD_PARTY", "new", "New Party", false),
            new PartySourceCandidate("SCALARIS_THIRD_PARTY", "new", "Duplicate", false),
            new PartySourceCandidate("UNKNOWN_SOURCE", "bad", "Bad", true),
            new PartySourceCandidate(null, "missing-type", "Missing Type", true)
        ));

        assertThat(report.totalCandidates()).isEqualTo(5);
        assertThat(report.changedNames()).isEqualTo(1);
        assertThat(report.newMappings()).isEqualTo(0);
        assertThat(report.hasBlockingFindings()).isTrue();
        assertThat(report.findings())
            .extracting(finding -> finding.code())
            .contains("DUPLICATE_SOURCE_KEY", "UNKNOWN_SOURCE_TYPE", "MISSING_SOURCE_TYPE");

        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from platform.party_reference where tenant_id = ?",
            Integer.class,
            account.tenantId().value()
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void sharedSchemaContainsOnlyReferenceFields() {
        var columns = jdbcTemplate.queryForList(
            """
            select column_name from information_schema.columns
             where table_schema = 'platform' and table_name = 'party_reference'
             order by ordinal_position
            """,
            String.class
        );

        assertThat(columns).containsExactly(
            "id",
            "tenant_id",
            "source_type",
            "source_id",
            "current_display_name",
            "status",
            "created_at",
            "updated_at",
            "version"
        );
        assertThat(columns).doesNotContain(
            "email",
            "document_number",
            "tax_id",
            "guardian_name",
            "address",
            "customer_kind",
            "supplier_kind"
        );
    }

    @Test
    void httpEnforcesAnonymousUnauthorizedAndOwnerSemantics() {
        var unauthorized = createAccount("unauthorized-http");
        var owner = createAccount("owner-http");
        authorizationAdministration.bootstrapOwner(owner.tenantId(), owner.membershipId());

        var anonymous = restTemplate.exchange(
            "/api/party-references",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            String.class
        );
        var forbidden = getParties(login(unauthorized));
        var imported = importParty(login(owner), unique("http-import"), Map.of(
            "sourceType", "GESTUDIO_STUDENT",
            "sourceId", "http-student",
            "displayName", "HTTP Student",
            "active", true
        ));
        var accepted = getParties(login(owner));

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(imported.getBody()).containsOnlyKeys("party", "mutation", "replayed");
        assertThat((Map<?, ?>) imported.getBody().get("party"))
            .containsOnlyKeys("id", "sourceType", "sourceId", "displayName", "active", "createdAt", "updatedAt");
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private AccountFixture createAccount(String prefix) {
        var tenantCode = unique(prefix);
        TenantId tenantId = provisioning.createTenant(tenantCode, prefix + " tenant");
        OrganizationId organizationId = provisioning.createOrganization(
            tenantId, "main", prefix + " organization");
        var branchId = provisioning.createBranch(
            tenantId, organizationId, "main", prefix + " branch");
        var subject = unique(prefix) + "@example.com";
        IdentityId identityId = provisioning.registerIdentity(subject);
        credentials.register(identityId, PASSWORD);
        MembershipId membershipId = provisioning.createMembership(
            tenantId, identityId, organizationId);
        provisioning.grantBranch(tenantId, membershipId, branchId);
        return new AccountFixture(tenantId, membershipId, tenantCode, subject);
    }

    private com.jereplatform.kernel.tenancy.api.TenantContext context(AccountFixture account) {
        return tenantAccessService.activate(
            account.externalSubject(),
            account.tenantCode(),
            UUID.randomUUID()
        );
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

    private org.springframework.http.ResponseEntity<Map> getParties(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(
            "/api/party-references",
            HttpMethod.GET,
            new HttpEntity<>(null, headers),
            Map.class
        );
    }

    private org.springframework.http.ResponseEntity<Map> importParty(
        String token,
        String idempotencyKey,
        Map<String, Object> request
    ) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange(
            "/api/party-references/imports",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            Map.class
        );
    }

    private static PartySourceRecord active(
        String sourceType,
        String sourceId,
        String displayName
    ) {
        return new PartySourceRecord(
            sourceType,
            sourceId,
            displayName,
            PartyLifecycleStatus.ACTIVE
        );
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record AccountFixture(
        TenantId tenantId,
        MembershipId membershipId,
        String tenantCode,
        String externalSubject
    ) {
    }
}
