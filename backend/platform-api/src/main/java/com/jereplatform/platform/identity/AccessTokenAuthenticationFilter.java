package com.jereplatform.platform.identity;

import com.jereplatform.kernel.identity.api.AuthenticationFailureException;
import com.jereplatform.kernel.identity.application.SessionValidationService;
import com.jereplatform.platform.tenancy.TenantContextFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final SessionValidationService sessionValidationService;

    public AccessTokenAuthenticationFilter(
        JwtTokenService jwtTokenService,
        SessionValidationService sessionValidationService
    ) {
        this.jwtTokenService = jwtTokenService;
        this.sessionValidationService = sessionValidationService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        var correlationId = parseCorrelationId(
            request.getHeader(TenantContextFilter.CORRELATION_HEADER),
            response
        );
        if (correlationId == null) {
            return;
        }

        try {
            var reference = jwtTokenService.verify(authorization.substring(BEARER_PREFIX.length()));
            var validated = sessionValidationService.validate(reference, correlationId);
            var principal = new PlatformPrincipal(
                validated.externalSubject(),
                validated.tenantCode(),
                validated.tenantContext(),
                validated.sessionFamilyId(),
                validated.credentialVersion()
            );
            var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            response.setHeader(TenantContextFilter.CORRELATION_HEADER, correlationId.toString());
            filterChain.doFilter(request, response);
        } catch (AuthenticationFailureException invalid) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Authentication required");
        }
    }

    private static UUID parseCorrelationId(String rawValue, HttpServletResponse response)
        throws IOException {
        if (rawValue == null || rawValue.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(rawValue.trim());
        } catch (IllegalArgumentException invalid) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid correlation identifier");
            return null;
        }
    }
}
