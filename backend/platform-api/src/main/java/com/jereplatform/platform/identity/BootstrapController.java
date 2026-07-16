package com.jereplatform.platform.identity;

import com.jereplatform.kernel.identity.api.BootstrapAdministrationCommand;
import com.jereplatform.kernel.identity.application.BootstrapAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bootstrap")
public class BootstrapController {

    public static final String BOOTSTRAP_TOKEN_HEADER = "X-Bootstrap-Token";

    private final BootstrapAdministrationService bootstrapAdministrationService;
    private final SecurityRuntimeProperties properties;

    public BootstrapController(
        BootstrapAdministrationService bootstrapAdministrationService,
        SecurityRuntimeProperties properties
    ) {
        this.bootstrapAdministrationService = bootstrapAdministrationService;
        this.properties = properties;
    }

    @PostMapping("/initialize")
    @ResponseStatus(HttpStatus.CREATED)
    public BootstrapResponse initialize(
        @RequestHeader(value = BOOTSTRAP_TOKEN_HEADER, required = false) String suppliedToken,
        @Valid @RequestBody BootstrapRequest request
    ) {
        verifyBootstrapToken(suppliedToken);
        var result = bootstrapAdministrationService.initialize(
            new BootstrapAdministrationCommand(
                request.tenantCode(),
                request.tenantDisplayName(),
                request.organizationCode(),
                request.organizationDisplayName(),
                request.branchCode(),
                request.branchDisplayName(),
                request.externalSubject(),
                request.password()
            )
        );
        return new BootstrapResponse(
            result.tenantId().value(),
            result.organizationId().value(),
            result.branchId().value(),
            result.identityId().value(),
            result.membershipId().value()
        );
    }

    private void verifyBootstrapToken(String suppliedToken) {
        var configuredToken = properties.bootstrapToken();
        if (configuredToken == null || configuredToken.isBlank()) {
            throw new BootstrapDisabledException();
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
            configuredToken.getBytes(StandardCharsets.UTF_8),
            suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new BootstrapAccessDeniedException();
        }
    }

    public record BootstrapRequest(
        @NotBlank @Size(max = 80) String tenantCode,
        @NotBlank @Size(max = 160) String tenantDisplayName,
        @NotBlank @Size(max = 80) String organizationCode,
        @NotBlank @Size(max = 160) String organizationDisplayName,
        @NotBlank @Size(max = 80) String branchCode,
        @NotBlank @Size(max = 160) String branchDisplayName,
        @NotBlank @Size(max = 190) String externalSubject,
        @NotBlank @Size(min = 12, max = 200) String password
    ) {
    }

    public record BootstrapResponse(
        UUID tenantId,
        UUID organizationId,
        UUID branchId,
        UUID identityId,
        UUID membershipId
    ) {
    }
}
