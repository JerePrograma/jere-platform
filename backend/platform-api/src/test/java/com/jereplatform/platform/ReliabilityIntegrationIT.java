package com.jereplatform.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jereplatform.kernel.authorization.application.AuthorizationAdministrationService;
import com.jereplatform.kernel.identity.application.CredentialRegistrationService;
import com.jereplatform.kernel.reliability.api.IdempotencyConflictException;
import com.jereplatform.kernel.reliability.api.OutboxEventDraft;
import com.jereplatform.kernel.reliability.api.ReliabilityCleanupPolicy;
import com.jereplatform.kernel.reliability.api.ReliableCommand;
import com.jereplatform.kernel.reliability.application.AuditQueryService;
import com.jereplatform.kernel.reliability.application.OutboxService;
import com.jereplatform.kernel.reliability.application.OutboxWorker;
import com.jereplatform.kernel.reliability.application.ReliabilityAdministrationService;
import com.jereplatform.kernel.reliability.application.ReliabilityCleanupService;
import com.jereplatform.kernel.reliability.application.ReliableCommandExecutor;
import com.jereplatform.kernel.reliability.internal.persistence.OutboxStore;
import com.jereplatform.kernel.tenancy.api.BranchId;
import com.jereplatform.kernel.tenancy.api.IdentityId;
import com.jereplatform.kernel.tenancy.api.MembershipId;
import com.jereplatform.kernel.tenancy.api.OrganizationId;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import com.jereplatform.kernel.tenancy.api.TenantId;
import com.jereplatform.kernel.tenancy.application.TenantAccessService;
import com.jereplatform.kernel.tenancy.application.TenantProvisioningService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Import(ReliabilityIntegrationIT.TestClockConfiguration.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "platform.security.jwt-secret=reliability-integration-secret-0123456789abcdef",
        "platform.security.access-token-lifetime=PT15M",
        "platform.security.refresh-token-lifetime=P30D"
    }
)
class ReliabilityIntegrationIT {

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
    private AuthorizationAdministrationService authorizationAdministration;

    @Autowired
    private TenantAccessService tenantAccessService;

    @Autowired
    private ReliableCommandExecutor commandExecutor;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private OutboxStore outboxStore;

    @Autowired
    private AuditQueryService auditQueryService;

    @Autowired
    private ReliabilityAdministrationService reliabilityAdministration;

    @Autowired
    private ReliabilityCleanupService cleanupService;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MutableReliabilityClock mutableClock;

    @BeforeEach
    void prepareDatabase() {
        mutableClock.reset();
        jdbcTemplate.execute("""
            create table if not exists platform.reliability_test_effect (
                tenant_id uuid not null references platform.tenant (id),
                effect_key varchar(160) not null,
                amount integer not null,
                primary key (tenant_id, effect_key)
            )
            """);
    }

    @Test
    void businessEffectOutboxAuditAndReplayAreAtomic() {
        var account = createAccount("atomic", true);
        var command = command(
            "effect.create",
            "raw-idempotency-key-that-must-not-be-stored",
            "request-one",
            "effect.atomic",
            "atomic-effect",
            Map.of("channel", "integration", "authorization", "must-be-removed"),
            Duration.ofDays(1)
        );
        var businessExecutions = new AtomicInteger();

        var first = commandExecutor.execute(
            account.context(),
            command,
            EffectResponse.class,
            () -> createEffectAndEvent(
                account.context(),
                "atomic-effect",
                25,
                businessExecutions
            )
        );
        var replay = commandExecutor.execute(
            account.context(),
            command,
            EffectResponse.class,
            () -> createEffectAndEvent(
                account.context(),
                "atomic-effect",
                25,
                businessExecutions
            )
        );

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.value()).isEqualTo(first.value());
        assertThat(businessExecutions).hasValue(1);
        assertThat(effectCount(account.tenantId(), "atomic-effect")).isEqualTo(1);
        assertThat(outboxCount(account.tenantId(), "test.effect.created")).isEqualTo(1);
        assertThat(idempotencyStatus(account.tenantId(), "effect.create"))
            .isEqualTo("COMPLETED");
        assertThat(rawIdempotencyKeyCount("raw-idempotency-key-that-must-not-be-stored"))
            .isZero();

        var audit = auditQueryService.latest(account.context(), 20).stream()
            .filter(event -> event.actionCode().equals("effect.atomic"))
            .toList();
        assertThat(audit).extracting(event -> event.result())
            .containsExactlyInAnyOrder("SUCCESS", "REPLAY");
        assertThat(audit).allSatisfy(event ->
            assertThat(event.metadata())
                .containsEntry("channel", "integration")
                .doesNotContainKey("authorization"));
    }

    @Test
    void sameKeyWithDifferentRequestHashIsRejectedWithoutSecondEffect() {
        var account = createAccount("conflict", true);
        var first = command(
            "effect.conflict",
            "shared-key",
            "payload-a",
            "effect.conflict",
            "conflict-effect",
            Map.of(),
            Duration.ofDays(1)
        );
        var second = command(
            "effect.conflict",
            "shared-key",
            "payload-b",
            "effect.conflict",
            "conflict-effect",
            Map.of(),
            Duration.ofDays(1)
        );

        commandExecutor.execute(
            account.context(),
            first,
            EffectResponse.class,
            () -> createEffectAndEvent(
                account.context(),
                "conflict-effect",
                1,
                new AtomicInteger()
            )
        );

        assertThatThrownBy(() -> commandExecutor.execute(
            account.context(),
            second,
            EffectResponse.class,
            () -> createEffectAndEvent(
                account.context(),
                "conflict-effect-two",
                2,
                new AtomicInteger()
            )
        )).isInstanceOf(IdempotencyConflictException.class);

        assertThat(effectCount(account.tenantId(), "conflict-effect")).isEqualTo(1);
        assertThat(effectCount(account.tenantId(), "conflict-effect-two")).isZero();
        assertThat(outboxCount(account.tenantId(), "test.effect.created")).isEqualTo(1);
    }

    @Test
    void rollbackRemovesBusinessAndOutboxButPersistsSanitizedFailureAudit() {
        var account = createAccount("rollback", true);
        var command = command(
            "effect.failure",
            "failure-key",
            "failure-request",
            "effect.failure",
            "rollback-effect",
            Map.of(
                "channel", "integration",
                "password", "secret-password-value",
                "refreshToken", "secret-refresh-value"
            ),
            Duration.ofDays(1)
        );

        assertThatThrownBy(() -> commandExecutor.execute(
            account.context(),
            command,
            EffectResponse.class,
            () -> {
                jdbcTemplate.update(
                    "insert into platform.reliability_test_effect (tenant_id, effect_key, amount) values (?, ?, ?)",
                    account.tenantId().value(),
                    "rollback-effect",
                    99
                );
                outboxService.enqueue(
                    account.context(),
                    new OutboxEventDraft(
                        "ReliabilityEffect",
                        "rollback-effect",
                        "test.effect.failed-before-commit",
                        Map.of("effectKey", "rollback-effect"),
                        Map.of("token", "must-not-be-persisted"),
                        3
                    )
                );
                throw new IllegalStateException("literal-secret-must-never-be-audited");
            }
        )).isInstanceOf(IllegalStateException.class);

        assertThat(effectCount(account.tenantId(), "rollback-effect")).isZero();
        assertThat(outboxCount(
            account.tenantId(),
            "test.effect.failed-before-commit"
        )).isZero();
        assertThat(idempotencyCount(account.tenantId(), "effect.failure")).isZero();

        var failure = auditQueryService.latest(account.context(), 20).stream()
            .filter(event -> event.actionCode().equals("effect.failure"))
            .findFirst()
            .orElseThrow();
        assertThat(failure.result()).isEqualTo("FAILURE");
        assertThat(failure.failureCode()).isEqualTo("ILLEGAL_STATE_EXCEPTION");
        assertThat(failure.metadata())
            .containsEntry("channel", "integration")
            .doesNotContainKeys("password", "refreshtoken");
        assertThat(secretAuditCount("literal-secret-must-never-be-audited")).isZero();
        assertThat(secretAuditCount("secret-password-value")).isZero();
        assertThat(secretAuditCount("secret-refresh-value")).isZero();
    }

    @Test
    void concurrentEquivalentRequestsProduceExactlyOneBusinessEffect() throws Exception {
        var account = createAccount("concurrent", true);
        var command = command(
            "effect.concurrent",
            "concurrent-key",
            "same-request",
            "effect.concurrent",
            "concurrent-effect",
            Map.of(),
            Duration.ofDays(1)
        );
        var supplierExecutions = new AtomicInteger();
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return commandExecutor.execute(
                    account.context(),
                    command,
                    EffectResponse.class,
                    () -> slowCreateEffectAndEvent(
                        account.context(),
                        "concurrent-effect",
                        supplierExecutions
                    )
                );
            });
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return commandExecutor.execute(
                    account.context(),
                    command,
                    EffectResponse.class,
                    () -> slowCreateEffectAndEvent(
                        account.context(),
                        "concurrent-effect",
                        supplierExecutions
                    )
                );
            });
            start.countDown();

            var results = java.util.List.of(
                first.get(15, TimeUnit.SECONDS),
                second.get(15, TimeUnit.SECONDS)
            );
            assertThat(results).filteredOn(result -> !result.replayed()).hasSize(1);
            assertThat(results).filteredOn(result -> result.replayed()).hasSize(1);
        }

        assertThat(supplierExecutions).hasValue(1);
        assertThat(effectCount(account.tenantId(), "concurrent-effect")).isEqualTo(1);
        assertThat(outboxCount(account.tenantId(), "test.effect.created")).isEqualTo(1);
    }

    @Test
    void outboxRetriesMovesToDeadAndCanBeRequeuedByItsTenantOnly() {
        var first = createAccount("dead-first", true);
        var second = createAccount("dead-second", true);
        var eventId = enqueue(first.context(), "dead-event", 2);

        var firstAttempt = outboxWorker.runOnce(
            10,
            Duration.ofMinutes(1),
            message -> { throw new IllegalStateException("temporary transport failure"); }
        );
        mutableClock.advance(Duration.ofSeconds(31));
        var secondAttempt = outboxWorker.runOnce(
            10,
            Duration.ofMinutes(1),
            message -> { throw new IllegalStateException("permanent transport failure"); }
        );

        assertThat(firstAttempt.scheduledForRetry()).isEqualTo(1);
        assertThat(secondAttempt.movedToDead()).isEqualTo(1);
        assertThat(reliabilityAdministration.deadOutbox(first.context(), 20))
            .extracting(failure -> failure.id())
            .contains(eventId);
        assertThat(reliabilityAdministration.deadOutbox(second.context(), 20)).isEmpty();
        assertThat(reliabilityAdministration.requeueDead(second.context(), eventId)).isFalse();
        assertThat(reliabilityAdministration.requeueDead(first.context(), eventId)).isTrue();

        var delivered = new AtomicReference<UUID>();
        var successfulRetry = outboxWorker.runOnce(
            10,
            Duration.ofMinutes(1),
            message -> delivered.set(message.id())
        );
        assertThat(successfulRetry.dispatched()).isEqualTo(1);
        assertThat(delivered).hasValue(eventId);
        assertThat(reliabilityAdministration.deadOutbox(first.context(), 20)).isEmpty();
    }

    @Test
    void expiredWorkerLeaseIsRecoveredWithoutLosingTheEvent() {
        var account = createAccount("lease", true);
        var eventId = enqueue(account.context(), "lease-event", 3);

        var abandoned = transactions.execute(status -> outboxStore.claimBatch(
            10,
            Duration.ofMinutes(1),
            mutableClock.instant()
        ));
        assertThat(abandoned).hasSize(1);
        assertThat(abandoned.getFirst().id()).isEqualTo(eventId);

        mutableClock.advance(Duration.ofMinutes(2));
        var delivered = new AtomicReference<UUID>();
        var recovered = outboxWorker.runOnce(
            10,
            Duration.ofMinutes(1),
            message -> delivered.set(message.id())
        );

        assertThat(recovered.claimed()).isEqualTo(1);
        assertThat(recovered.dispatched()).isEqualTo(1);
        assertThat(delivered).hasValue(eventId);
    }

    @Test
    void auditRowsCannotBeUpdatedOrDeleted() {
        var account = createAccount("append-only", true);
        var command = command(
            "effect.audit-lock",
            "audit-lock-key",
            "audit-lock-request",
            "effect.audit-lock",
            "audit-lock-effect",
            Map.of(),
            Duration.ofDays(1)
        );
        commandExecutor.execute(
            account.context(),
            command,
            EffectResponse.class,
            () -> createEffectAndEvent(
                account.context(),
                "audit-lock-effect",
                1,
                new AtomicInteger()
            )
        );
        var auditId = jdbcTemplate.queryForObject(
            "select id from platform.audit_event where tenant_id = ? and action_code = ? limit 1",
            UUID.class,
            account.tenantId().value(),
            "effect.audit-lock"
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            "update platform.audit_event set action_code = 'tampered' where id = ?",
            auditId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "delete from platform.audit_event where id = ?",
            auditId
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void cleanupRemovesOnlyExpiredReplayAndDispatchedRecords() {
        var account = createAccount("cleanup", true);
        var command = command(
            "effect.cleanup",
            "cleanup-key",
            "cleanup-request",
            "effect.cleanup",
            "cleanup-effect",
            Map.of(),
            Duration.ofMinutes(1)
        );
        commandExecutor.execute(
            account.context(),
            command,
            EffectResponse.class,
            () -> createEffectAndEvent(
                account.context(),
                "cleanup-effect",
                1,
                new AtomicInteger()
            )
        );
        outboxWorker.runOnce(10, Duration.ofMinutes(1), message -> { });

        var deadEvent = enqueue(account.context(), "cleanup-dead", 1);
        outboxWorker.runOnce(
            10,
            Duration.ofMinutes(1),
            message -> { throw new IllegalStateException("terminal"); }
        );
        assertThat(reliabilityAdministration.deadOutbox(account.context(), 20))
            .extracting(failure -> failure.id())
            .contains(deadEvent);
        var auditBefore = auditQueryService.latest(account.context(), 200).size();

        mutableClock.advance(Duration.ofDays(2));
        var cleanup = cleanupService.cleanup(
            new ReliabilityCleanupPolicy(Duration.ofDays(1), 100)
        );

        assertThat(cleanup.deletedIdempotencyRecords()).isGreaterThanOrEqualTo(1);
        assertThat(cleanup.deletedDispatchedOutboxEvents()).isGreaterThanOrEqualTo(1);
        assertThat(idempotencyCount(account.tenantId(), "effect.cleanup")).isZero();
        assertThat(outboxStatusCount(account.tenantId(), "DISPATCHED")).isZero();
        assertThat(reliabilityAdministration.deadOutbox(account.context(), 20))
            .extracting(failure -> failure.id())
            .contains(deadEvent);
        assertThat(auditQueryService.latest(account.context(), 200)).hasSize(auditBefore);
    }

    @Test
    void ownerCanInspectReliabilityWhileUnassignedMembershipReceives403() {
        var owner = createAccount("http-owner", true);
        var viewer = createAccount("http-unassigned", false);
        var ownerToken = login(owner);
        var viewerToken = login(viewer);

        assertThat(get("/api/audit", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/audit", viewerToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/reliability/summary", viewerToken).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/audit", ownerToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/reliability/summary", ownerToken).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }

    private EffectResponse createEffectAndEvent(
        TenantContext context,
        String effectKey,
        int amount,
        AtomicInteger executions
    ) {
        executions.incrementAndGet();
        jdbcTemplate.update(
            "insert into platform.reliability_test_effect (tenant_id, effect_key, amount) values (?, ?, ?)",
            context.tenantId().value(),
            effectKey,
            amount
        );
        outboxService.enqueue(
            context,
            new OutboxEventDraft(
                "ReliabilityEffect",
                effectKey,
                "test.effect.created",
                Map.of("effectKey", effectKey, "amount", amount),
                Map.of("channel", "integration", "authorization", "removed"),
                3
            )
        );
        return new EffectResponse(effectKey, amount);
    }

    private EffectResponse slowCreateEffectAndEvent(
        TenantContext context,
        String effectKey,
        AtomicInteger executions
    ) {
        try {
            Thread.sleep(250);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", interrupted);
        }
        return createEffectAndEvent(context, effectKey, 7, executions);
    }

    private UUID enqueue(TenantContext context, String aggregateId, int maxAttempts) {
        var eventId = transactions.execute(status -> outboxService.enqueue(
            context,
            new OutboxEventDraft(
                "ReliabilityEffect",
                aggregateId,
                "test.outbox.delivery",
                Map.of("aggregateId", aggregateId),
                Map.of("channel", "integration"),
                maxAttempts
            )
        ));
        if (eventId == null) {
            throw new IllegalStateException("Outbox transaction returned no event identifier");
        }
        return eventId;
    }

    private AccountFixture createAccount(String prefix, boolean owner) {
        var tenantCode = unique(prefix);
        TenantId tenantId = provisioning.createTenant(tenantCode, prefix + " tenant");
        OrganizationId organizationId = provisioning.createOrganization(
            tenantId,
            "main",
            prefix + " organization"
        );
        BranchId branchId = provisioning.createBranch(
            tenantId,
            organizationId,
            "main",
            prefix + " branch"
        );
        var subject = unique(prefix) + "@example.com";
        IdentityId identityId = provisioning.registerIdentity(subject);
        credentials.register(identityId, PASSWORD);
        MembershipId membershipId = provisioning.createMembership(
            tenantId,
            identityId,
            organizationId
        );
        provisioning.grantBranch(tenantId, membershipId, branchId);
        authorizationAdministration.reconcileBaseRoles(tenantId);
        if (owner) {
            authorizationAdministration.assignBaseRole(
                tenantId,
                membershipId,
                AuthorizationAdministrationService.OWNER_SYSTEM_KEY,
                null
            );
        }
        var context = tenantAccessService.activate(subject, tenantCode, UUID.randomUUID());
        return new AccountFixture(
            tenantId,
            organizationId,
            branchId,
            identityId,
            membershipId,
            tenantCode,
            subject,
            context
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

    private org.springframework.http.ResponseEntity<Map> get(String path, String token) {
        var headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(
            path,
            HttpMethod.GET,
            new HttpEntity<>(null, headers),
            Map.class
        );
    }

    private ReliableCommand command(
        String operationCode,
        String key,
        String request,
        String actionCode,
        String targetId,
        Map<String, String> metadata,
        Duration retention
    ) {
        return new ReliableCommand(
            operationCode,
            key,
            request.getBytes(StandardCharsets.UTF_8),
            actionCode,
            "ReliabilityEffect",
            targetId,
            metadata,
            retention
        );
    }

    private long effectCount(TenantId tenantId, String effectKey) {
        return jdbcTemplate.queryForObject(
            "select count(*) from platform.reliability_test_effect where tenant_id = ? and effect_key = ?",
            Long.class,
            tenantId.value(),
            effectKey
        );
    }

    private long outboxCount(TenantId tenantId, String eventType) {
        return jdbcTemplate.queryForObject(
            "select count(*) from platform.outbox_event where tenant_id = ? and event_type = ?",
            Long.class,
            tenantId.value(),
            eventType
        );
    }

    private long outboxStatusCount(TenantId tenantId, String status) {
        return jdbcTemplate.queryForObject(
            "select count(*) from platform.outbox_event where tenant_id = ? and status = ?",
            Long.class,
            tenantId.value(),
            status
        );
    }

    private long idempotencyCount(TenantId tenantId, String operationCode) {
        return jdbcTemplate.queryForObject(
            "select count(*) from platform.idempotency_record where tenant_id = ? and operation_code = ?",
            Long.class,
            tenantId.value(),
            operationCode
        );
    }

    private String idempotencyStatus(TenantId tenantId, String operationCode) {
        return jdbcTemplate.queryForObject(
            "select status from platform.idempotency_record where tenant_id = ? and operation_code = ?",
            String.class,
            tenantId.value(),
            operationCode
        );
    }

    private long rawIdempotencyKeyCount(String rawKey) {
        return jdbcTemplate.queryForObject(
            "select count(*) from platform.idempotency_record where key_hash = ?",
            Long.class,
            rawKey
        );
    }

    private long secretAuditCount(String secret) {
        return jdbcTemplate.queryForObject(
            "select count(*) from platform.audit_event where metadata::text like ? or coalesce(failure_code, '') like ?",
            Long.class,
            "%" + secret + "%",
            "%" + secret + "%"
        );
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public record EffectResponse(String effectKey, int amount) {
    }

    private record AccountFixture(
        TenantId tenantId,
        OrganizationId organizationId,
        BranchId branchId,
        IdentityId identityId,
        MembershipId membershipId,
        String tenantCode,
        String externalSubject,
        TenantContext context
    ) {
    }

    @TestConfiguration
    static class TestClockConfiguration {

        @Bean
        MutableReliabilityClock mutableReliabilityClock() {
            return new MutableReliabilityClock();
        }

        @Bean
        @Primary
        Clock reliabilityTestClock(MutableReliabilityClock mutableClock) {
            return mutableClock;
        }
    }

    static final class MutableReliabilityClock extends Clock {

        private final AtomicReference<Instant> current = new AtomicReference<>();

        MutableReliabilityClock() {
            reset();
        }

        void reset() {
            current.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        }

        void advance(Duration duration) {
            current.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
