package com.jereplatform.platform.authorization;

import com.jereplatform.kernel.authorization.application.AuthorizationService;
import com.jereplatform.kernel.tenancy.api.BranchId;
import com.jereplatform.platform.identity.PlatformPrincipal;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("platformAuthorization")
public class PlatformAuthorizationFacade {

    private final AuthorizationService authorizationService;

    public PlatformAuthorizationFacade(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public boolean can(Authentication authentication, String permissionCode) {
        var principal = principal(authentication);
        return principal != null && authorizationService.hasPermission(
            principal.tenantContext(),
            permissionCode,
            null
        );
    }

    public boolean canForBranch(
        Authentication authentication,
        String permissionCode,
        UUID branchId
    ) {
        var principal = principal(authentication);
        return principal != null && branchId != null && authorizationService.hasPermission(
            principal.tenantContext(),
            permissionCode,
            new BranchId(branchId)
        );
    }

    private static PlatformPrincipal principal(Authentication authentication) {
        if (authentication != null
            && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof PlatformPrincipal principal) {
            return principal;
        }
        return null;
    }
}
