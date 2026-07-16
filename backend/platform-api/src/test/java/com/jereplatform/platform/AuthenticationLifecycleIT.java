package com.jereplatform.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jereplatform.kernel.identity.api.AuthenticationFailureException;
import com.jereplatform.kernel.identity.application.AuthenticationService;
import com.jereplatform.kernel.identity.application.CredentialRegistrationService;
import com.jereplatform.kernel.identity.application.SessionValidationService;
import com.jereplatform.kernel.tenancy.api.IdentityId;
import com.jereplatform.kernel.tenancy.api.MembershipId;
import com.jereplatform.kernel.tenancy.api.OrganizationId;
import com.jereplatform.kernel.tenancy.api.TenantId;
import com.jereplatform.kernel.tenancy.application.TenantProvisioningService;
import com.jereplatform.platform.identity.AuthenticationController;
import com.jereplatform.platform.identity.BootstrapController;
import com.jereplatform.platform.identity.JwtTokenService;
import com.jereplatform.platform.identity.RefreshCookieService;
import com.jereplatform.platform.tenancy.TenantContextFilter;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Import(AuthenticationLifecycleIT.TestClockConfiguration.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "platform.security.jwt-secret=0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.access-token-lifetime=PT15M",
        "platform.security.refresh-token-lifetime=P30D",
        "platform.security.refresh-cookie-secure=true",
        "platform.security.bootstrap-token=bootstrap-integration-secret"
    }
)
class AuthenticationLifecycleIT {

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
    private CredentialRegistrationService credentialRegistrationService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private SessionValidationService sessionValidationService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AdjustableClock clock;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void bootstrapIsGuardedTransactionalAndOneTime() {
        var request = Map.of(
            "tenantCode", unique("bootstrap"),
            "tenantDisplayName", "Bootstrap Tenant",
            "organizationCode", "main",
            "organizationDisplayName", "Bootstrap Organization",
            "branchCode", "main",
            "branchDisplayName", "Bootstrap Branch",
            "externalSubject", unique("bootstrap") + "@example.com",
            "password", PASSWORD
        );
        var headers = jsonHeaders();
        headers.set(BootstrapController.BOOTSTRAP_TOKEN_HEADER, "bootstrap-integration-secret");

        var created = restTemplate.exchange(
            "/api/bootstrap/initialize",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            Map.class
        );
        var repeated = restTemplate.exchange(
            "/api/bootstrap/initialize",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            Map.class
        );

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsKeys(
            "tenantId", "organizationId", "branchId", "identityId", "membershipId");
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidCredentialResponsesDoNotRevealWhetherIdentityExists() {
        var account = createAccount("generic-error");

        var wrongPassword = loginHttp(
            account.externalSubject(),
            "definitely-not-the-password",
            account.tenantCode()
        );
        var unknownIdentity = loginHttp(
            unique("missing") + "@example.com",
            "definitely-not-the-password",
            account.tenantCode()
        );

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownIdentity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody()).isEqualTo(unknownIdentity.getBody());
        assertThat(wrongPassword.getBody()).containsEntry("code", "authentication_failed");
    }

    @Test
    void secureRefreshCookieIsHttpOnlyAndBearerCannotSwitchTenant() {
        var account = createAccount("cookie");
        var secondTenant = addSecondTenantMembership(account);

        var login = loginHttp(account.externalSubject(), PASSWORD, account.tenantCode());

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        var setCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
            .contains("HttpOnly")
            .contains("Secure")
            .contains("SameSite=Strict")
            .contains("Path=/api/auth");
        assertThat(login.getBody()).doesNotContainKeys("refreshToken", "password");

        var accessToken = (String) login.getBody().get("accessToken");
        var accepted = currentSession(accessToken, account.tenantCode());
        var switched = currentSession(accessToken, secondTenant.tenantCode());

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody()).containsEntry("tenantCode", account.tenantCode());
        assertThat(switched.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void refreshRequiresIntentRotatesAndReplayCompromisesTheFamily() {
        var account = createAccount("rotation");
        var login = loginHttp(account.externalSubject(), PASSWORD, account.tenantCode());
        var firstCookie = cookiePair(login);

        var noIntent = refreshHttp(firstCookie, false);
        var rotated = refreshHttp(firstCookie, true);
        var secondCookie = cookiePair(rotated);
        var replay = refreshHttp(firstCookie, true);
        var replacementAfterReplay = refreshHttp(secondCookie, true);

        assertThat(noIntent.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondCookie).isNotEqualTo(firstCookie);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replacementAfterReplay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesTheWholeSessionFamily() {
        var account = createAccount("logout");
        var login = loginHttp(account.externalSubject(), PASSWORD, account.tenantCode());
        var cookie = cookiePair(login);

        var headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.set(RefreshCookieService.INTENT_HEADER, RefreshCookieService.INTENT_VALUE);
        var logout = restTemplate.exchange(
            "/api/auth/logout",
            HttpMethod.POST,
            new HttpEntity<>(null, headers),
            Void.class
        );
        var refresh = refreshHttp(cookie, true);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
            .contains("Max-Age=0");
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void membershipRevocationBlocksRefreshAndExistingAccessToken() {
        var account = createAccount("membership-revocation");
        var login = loginHttp(account.externalSubject(), PASSWORD, account.tenantCode());
        var accessToken = (String) login.getBody().get("accessToken");
        var cookie = cookiePair(login);

        transactionTemplate.executeWithoutResult(status ->
            provisioning.revokeMembership(account.tenantId(), account.membershipId()));

        assertThat(currentSession(accessToken, account.tenantCode()).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refreshHttp(cookie, true).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordReplacementInvalidatesExistingAccessAndRefreshSessions() {
        var account = createAccount("password-change");
        var grant = authenticationService.login(
            account.externalSubject(),
            PASSWORD,
            account.tenantCode(),
            UUID.randomUUID()
        );
        var issued = jwtTokenService.issue(grant);
        var reference = jwtTokenService.verify(issued.value());

        credentialRegistrationService.replacePassword(
            account.identityId(),
            "a-new-password-that-is-long-enough"
        );

        assertThatThrownBy(() -> sessionValidationService.validate(reference, UUID.randomUUID()))
            .isInstanceOf(AuthenticationFailureException.class);
        assertThatThrownBy(() ->
            authenticationService.refresh(grant.refreshToken(), UUID.randomUUID()))
            .isInstanceOf(AuthenticationFailureException.class);
    }

    @Test
    void refreshExpirationUsesTheConfiguredClockWithoutSleeping() {
        var account = createAccount("expiry");
        var grant = authenticationService.login(
            account.externalSubject(),
            PASSWORD,
            account.tenantCode(),
            UUID.randomUUID()
        );

        clock.advance(Duration.ofDays(31));

        assertThatThrownBy(() ->
            authenticationService.refresh(grant.refreshToken(), UUID.randomUUID()))
            .isInstanceOf(AuthenticationFailureException.class);
    }

    @Test
    void concurrentRefreshDoesNotProduceTwoUsableSuccessors() throws Exception {
        var account = createAccount("concurrent");
        var grant = authenticationService.login(
            account.externalSubject(),
            PASSWORD,
            account.tenantCode(),
            UUID.randomUUID()
        );
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> refreshConcurrently(grant.refreshToken(), start));
            var second = executor.submit(() -> refreshConcurrently(grant.refreshToken(), start));
            start.countDown();

            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);
            var successes = java.util.stream.Stream.of(firstResult, secondResult)
                .filter(ConcurrentRefreshResult::success)
                .toList();

            assertThat(successes).hasSize(1);
            assertThat(java.util.stream.Stream.of(firstResult, secondResult)
                .filter(result -> !result.success()).count()).isEqualTo(1);
            assertThatThrownBy(() -> authenticationService.refresh(
                successes.getFirst().refreshToken(),
                UUID.randomUUID()
            )).isInstanceOf(AuthenticationFailureException.class);
        }
    }

    private ConcurrentRefreshResult refreshConcurrently(
        String refreshToken,
        CountDownLatch start
    ) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        try {
            var grant = authenticationService.refresh(refreshToken, UUID.randomUUID());
            return new ConcurrentRefreshResult(true, grant.refreshToken());
        } catch (AuthenticationFailureException expected) {
            return new ConcurrentRefreshResult(false, null);
        }
    }

    private AccountFixture createAccount(String prefix) {
        return transactionTemplate.execute(status -> {
            var tenantCode = unique(prefix);
            TenantId tenantId = provisioning.createTenant(tenantCode, prefix + " tenant");
            OrganizationId organizationId = provisioning.createOrganization(
                tenantId, "main", prefix + " organization");
            var branchId = provisioning.createBranch(
                tenantId, organizationId, "main", prefix + " branch");
            var subject = unique(prefix) + "@example.com";
            IdentityId identityId = provisioning.registerIdentity(subject);
            credentialRegistrationService.register(identityId, PASSWORD);
            MembershipId membershipId = provisioning.createMembership(
                tenantId, identityId, organizationId);
            provisioning.grantBranch(tenantId, membershipId, branchId);
            return new AccountFixture(
                tenantId,
                organizationId,
                identityId,
                membershipId,
                tenantCode,
                subject
            );
        });
    }

    private AccountFixture addSecondTenantMembership(AccountFixture account) {
        return transactionTemplate.execute(status -> {
            var tenantCode = unique("second");
            TenantId tenantId = provisioning.createTenant(tenantCode, "Second tenant");
            OrganizationId organizationId = provisioning.createOrganization(
                tenantId, "main", "Second organization");
            var branchId = provisioning.createBranch(
                tenantId, organizationId, "main", "Second branch");
            MembershipId membershipId = provisioning.createMembership(
                tenantId, account.identityId(), organizationId);
            provisioning.grantBranch(tenantId, membershipId, branchId);
            return new AccountFixture(
                tenantId,
                organizationId,
                account.identityId(),
                membershipId,
                tenantCode,
                account.externalSubject()
            );
        });
    }

    private org.springframework.http.ResponseEntity<Map> loginHttp(
        String externalSubject,
        String password,
        String tenantCode
    ) {
        var headers = jsonHeaders();
        return restTemplate.exchange(
            "/api/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "externalSubject", externalSubject,
                "password", password,
                "tenantCode", tenantCode
            ), headers),
            Map.class
        );
    }

    private org.springframework.http.ResponseEntity<Map> refreshHttp(
        String cookie,
        boolean includeIntent
    ) {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        if (includeIntent) {
            headers.set(RefreshCookieService.INTENT_HEADER, RefreshCookieService.INTENT_VALUE);
        }
        return restTemplate.exchange(
            "/api/auth/refresh",
            HttpMethod.POST,
            new HttpEntity<>(null, headers),
            Map.class
        );
    }

    private org.springframework.http.ResponseEntity<Map> currentSession(
        String accessToken,
        String tenantCode
    ) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set(TenantContextFilter.TENANT_HEADER, tenantCode);
        return restTemplate.exchange(
            "/api/session",
            HttpMethod.GET,
            new HttpEntity<>(null, headers),
            Map.class
        );
    }

    private static HttpHeaders jsonHeaders() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static String cookiePair(org.springframework.http.ResponseEntity<?> response) {
        var setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record AccountFixture(
        TenantId tenantId,
        OrganizationId organizationId,
        IdentityId identityId,
        MembershipId membershipId,
        String tenantCode,
        String externalSubject
    ) {
    }

    private record ConcurrentRefreshResult(boolean success, String refreshToken) {
    }

    @TestConfiguration
    static class TestClockConfiguration {

        @Bean
        @Primary
        AdjustableClock adjustableClock() {
            return new AdjustableClock();
        }

        @Bean
        @Primary
        Clock testClock(AdjustableClock adjustableClock) {
            return adjustableClock;
        }
    }
}
