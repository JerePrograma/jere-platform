package com.jereplatform.platform.reliability;

import com.jereplatform.kernel.reliability.api.OutboxFailureView;
import com.jereplatform.kernel.reliability.api.ReliabilitySummary;
import com.jereplatform.kernel.reliability.application.ReliabilityAdministrationService;
import com.jereplatform.platform.identity.PlatformPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reliability")
public class ReliabilityController {

    private final ReliabilityAdministrationService administrationService;

    public ReliabilityController(ReliabilityAdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    @GetMapping("/summary")
    @PreAuthorize("@platformAuthorization.can(authentication, 'platform.reliability.read')")
    public ReliabilitySummary summary(Authentication authentication) {
        return administrationService.summary(principal(authentication).tenantContext());
    }

    @GetMapping("/outbox/dead")
    @PreAuthorize("@platformAuthorization.can(authentication, 'platform.reliability.read')")
    public List<OutboxFailureView> deadOutbox(
        Authentication authentication,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return administrationService.deadOutbox(
            principal(authentication).tenantContext(),
            limit
        );
    }

    @PostMapping("/outbox/dead/{eventId}/requeue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@platformAuthorization.can(authentication, 'platform.reliability.manage')")
    public Map<String, Boolean> requeue(
        Authentication authentication,
        @PathVariable UUID eventId
    ) {
        return Map.of(
            "requeued",
            administrationService.requeueDead(
                principal(authentication).tenantContext(),
                eventId
            )
        );
    }

    private static PlatformPrincipal principal(Authentication authentication) {
        return (PlatformPrincipal) authentication.getPrincipal();
    }
}
