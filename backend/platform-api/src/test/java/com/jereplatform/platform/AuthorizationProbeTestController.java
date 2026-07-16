package com.jereplatform.platform;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/authorization/probe")
class AuthorizationProbeTestController {

    @GetMapping("/tenant")
    @PreAuthorize("@platformAuthorization.can(authentication, 'platform.session.read')")
    Map<String, Boolean> tenantProbe() {
        return Map.of("allowed", true);
    }

    @GetMapping("/branch/{branchId}")
    @PreAuthorize(
        "@platformAuthorization.canForBranch(authentication, 'academy.students.read', #branchId)"
    )
    Map<String, Object> branchProbe(@PathVariable UUID branchId) {
        return Map.of("allowed", true, "branchId", branchId);
    }
}
