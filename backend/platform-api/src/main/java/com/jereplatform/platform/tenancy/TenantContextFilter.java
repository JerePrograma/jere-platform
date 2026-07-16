package com.jereplatform.platform.tenancy;

import com.jereplatform.kernel.tenancy.api.TenantAccessDeniedException;
import com.jereplatform.kernel.tenancy.api.TenantContextHolder;
import com.jereplatform.kernel.tenancy.application.TenantAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Code";
    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final TenantAccessService tenantAccessService;

    public TenantContextFilter(TenantAccessService tenantAccessService) {
        this.tenantAccessService = tenantAccessService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        var principal = request.getUserPrincipal();
        var requestedTenant = request.getHeader(TENANT_HEADER);

        if (principal == null || requestedTenant == null || requestedTenant.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        var correlationId = parseCorrelationId(request.getHeader(CORRELATION_HEADER), response);
        if (correlationId == null) {
            return;
        }

        try {
            var context = tenantAccessService.activate(
                principal.getName(),
                requestedTenant,
                correlationId
            );
            response.setHeader(CORRELATION_HEADER, correlationId.toString());
            try (var ignored = TenantContextHolder.open(context)) {
                filterChain.doFilter(request, response);
            }
        } catch (TenantAccessDeniedException denied) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Tenant access denied");
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
