package com.jereplatform.platform.reliability;

import com.jereplatform.kernel.reliability.application.AuditQueryService;
import com.jereplatform.platform.identity.PlatformPrincipal;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @PreAuthorize("@platformAuthorization.can(authentication, 'platform.audit.read')")
    public Map<String, Object> latest(
        Authentication authentication,
        @RequestParam(defaultValue = "50") int limit
    ) {
        var principal = (PlatformPrincipal) authentication.getPrincipal();
        var events = auditQueryService.latest(principal.tenantContext(), limit);
        return Map.of(
            "events", events,
            "count", events.size()
        );
    }
}
