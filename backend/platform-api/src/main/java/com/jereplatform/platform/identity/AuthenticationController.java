package com.jereplatform.platform.identity;

import com.jereplatform.kernel.identity.api.AuthenticationFailureException;
import com.jereplatform.kernel.identity.api.SessionGrant;
import com.jereplatform.kernel.identity.application.AuthenticationService;
import com.jereplatform.platform.tenancy.TenantContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final JwtTokenService jwtTokenService;
    private final RefreshCookieService refreshCookieService;

    public AuthenticationController(
        AuthenticationService authenticationService,
        JwtTokenService jwtTokenService,
        RefreshCookieService refreshCookieService
    ) {
        this.authenticationService = authenticationService;
        this.jwtTokenService = jwtTokenService;
        this.refreshCookieService = refreshCookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(
        @Valid @RequestBody LoginRequest request,
        @RequestHeader(value = TenantContextFilter.CORRELATION_HEADER, required = false)
        String rawCorrelationId
    ) {
        var correlationId = parseCorrelationId(rawCorrelationId);
        var grant = authenticationService.login(
            request.externalSubject(),
            request.password(),
            request.tenantCode(),
            correlationId
        );
        return tokenResponse(grant, correlationId);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
        HttpServletRequest request,
        @RequestHeader(value = TenantContextFilter.CORRELATION_HEADER, required = false)
        String rawCorrelationId
    ) {
        refreshCookieService.requireIntent(request);
        var rawRefreshToken = refreshCookieService.read(request);
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthenticationFailureException();
        }
        var correlationId = parseCorrelationId(rawCorrelationId);
        var grant = authenticationService.refresh(rawRefreshToken, correlationId);
        return tokenResponse(grant, correlationId);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        refreshCookieService.requireIntent(request);
        authenticationService.logout(refreshCookieService.read(request));
        var headers = new HttpHeaders();
        refreshCookieService.clear(headers);
        return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
    }

    private ResponseEntity<AccessTokenResponse> tokenResponse(
        SessionGrant grant,
        UUID correlationId
    ) {
        var accessToken = jwtTokenService.issue(grant);
        var headers = new HttpHeaders();
        refreshCookieService.write(headers, grant);
        headers.set(TenantContextFilter.CORRELATION_HEADER, correlationId.toString());
        var context = grant.tenantContext();
        return new ResponseEntity<>(new AccessTokenResponse(
            accessToken.value(),
            "Bearer",
            accessToken.expiresAt(),
            grant.tenantCode(),
            context.identityId().value(),
            context.membershipId().value()
        ), headers, HttpStatus.OK);
    }

    private static UUID parseCorrelationId(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(rawValue.trim());
        } catch (IllegalArgumentException invalid) {
            throw new InvalidCorrelationIdException();
        }
    }

    public record LoginRequest(
        @NotBlank String externalSubject,
        @NotBlank String password,
        @NotBlank String tenantCode
    ) {
    }

    public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        String tenantCode,
        UUID identityId,
        UUID membershipId
    ) {
    }
}
