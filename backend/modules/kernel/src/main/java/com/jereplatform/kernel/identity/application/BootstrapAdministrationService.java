package com.jereplatform.kernel.identity.application;

import com.jereplatform.kernel.identity.api.BootstrapAdministrationCommand;
import com.jereplatform.kernel.identity.api.BootstrapAdministrationResult;
import com.jereplatform.kernel.identity.internal.persistence.BootstrapMarkerRepository;
import com.jereplatform.kernel.tenancy.application.TenantProvisioningService;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapAdministrationService {

    private final BootstrapMarkerRepository bootstrapMarkerRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final CredentialRegistrationService credentialRegistrationService;
    private final Clock clock;

    public BootstrapAdministrationService(
        BootstrapMarkerRepository bootstrapMarkerRepository,
        TenantProvisioningService tenantProvisioningService,
        CredentialRegistrationService credentialRegistrationService,
        Clock clock
    ) {
        this.bootstrapMarkerRepository = bootstrapMarkerRepository;
        this.tenantProvisioningService = tenantProvisioningService;
        this.credentialRegistrationService = credentialRegistrationService;
        this.clock = clock;
    }

    @Transactional
    public BootstrapAdministrationResult initialize(BootstrapAdministrationCommand command) {
        var marker = bootstrapMarkerRepository.lockSingleton()
            .orElseThrow(() -> new IllegalStateException("Platform bootstrap marker is missing"));
        marker.claimIdentityBootstrap(clock.instant());

        var tenantId = tenantProvisioningService.createTenant(
            command.tenantCode(),
            command.tenantDisplayName()
        );
        var organizationId = tenantProvisioningService.createOrganization(
            tenantId,
            command.organizationCode(),
            command.organizationDisplayName()
        );
        var branchId = tenantProvisioningService.createBranch(
            tenantId,
            organizationId,
            command.branchCode(),
            command.branchDisplayName()
        );
        var identityId = tenantProvisioningService.registerIdentity(command.externalSubject());
        credentialRegistrationService.register(identityId, command.password());
        var membershipId = tenantProvisioningService.createMembership(
            tenantId,
            identityId,
            organizationId
        );
        tenantProvisioningService.grantBranch(tenantId, membershipId, branchId);

        return new BootstrapAdministrationResult(
            tenantId,
            organizationId,
            branchId,
            identityId,
            membershipId
        );
    }
}
