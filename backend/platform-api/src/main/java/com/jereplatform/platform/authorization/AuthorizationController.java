package com.jereplatform.platform.authorization;

import com.jereplatform.kernel.authorization.api.AuthorizationSnapshot;
import com.jereplatform.kernel.authorization.application.AuthorizationService;
import com.jereplatform.platform.identity.PlatformPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/authorization")
public class AuthorizationController {

    private final AuthorizationService authorizationService;

    public AuthorizationController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @GetMapping("/current")
    @PreAuthorize("@platformAuthorization.can(authentication, 'platform.session.read')")
    public AuthorizationSnapshot current(Authentication authentication) {
        var principal = (PlatformPrincipal) authentication.getPrincipal();
        return authorizationService.snapshot(principal.tenantContext());
    }
}
