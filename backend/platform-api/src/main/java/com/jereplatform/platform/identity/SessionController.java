package com.jereplatform.platform.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    @GetMapping
    public SessionResponse current(Authentication authentication) {
        var principal = (PlatformPrincipal) authentication.getPrincipal();
        var context = principal.tenantContext();
        return new SessionResponse(
            principal.getName(),
            principal.tenantCode(),
            context.identityId().value(),
            context.membershipId().value(),
            context.branchIds().stream().map(branch -> branch.value()).sorted().toList(),
            context.correlationId()
        );
    }

    public record SessionResponse(
        String externalSubject,
        String tenantCode,
        UUID identityId,
        UUID membershipId,
        List<UUID> branchIds,
        UUID correlationId
    ) {
    }
}
