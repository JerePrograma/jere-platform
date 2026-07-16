package com.jereplatform.kernel.identity.application;

import com.jereplatform.kernel.identity.api.AuthenticationFailureException;
import com.jereplatform.kernel.identity.internal.persistence.CredentialRepository;
import com.jereplatform.kernel.identity.internal.persistence.CredentialStatus;
import com.jereplatform.kernel.identity.internal.persistence.SessionFamilyEntity;
import com.jereplatform.kernel.tenancy.application.TenantAccessService;
import com.jereplatform.kernel.tenancy.internal.persistence.IdentityRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.LifecycleStatus;
import com.jereplatform.kernel.tenancy.internal.persistence.MembershipRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.TenantRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SessionContextResolver {

    private final CredentialRepository credentialRepository;
    private final IdentityRepository identityRepository;
    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;
    private final TenantAccessService tenantAccessService;

    SessionContextResolver(
        CredentialRepository credentialRepository,
        IdentityRepository identityRepository,
        TenantRepository tenantRepository,
        MembershipRepository membershipRepository,
        TenantAccessService tenantAccessService
    ) {
        this.credentialRepository = credentialRepository;
        this.identityRepository = identityRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.tenantAccessService = tenantAccessService;
    }

    ResolvedSessionDetails resolve(
        SessionFamilyEntity family,
        Instant now,
        UUID correlationId
    ) {
        if (!family.isUsableAt(now)) {
            throw new AuthenticationFailureException();
        }

        var credential = credentialRepository
            .findByIdentityIdAndStatus(family.getIdentityId(), CredentialStatus.ACTIVE)
            .filter(current -> current.getInvalidationVersion() == family.getCredentialVersion())
            .orElseThrow(AuthenticationFailureException::new);

        var identity = identityRepository.findById(family.getIdentityId())
            .filter(current -> current.getStatus() == LifecycleStatus.ACTIVE)
            .orElseThrow(AuthenticationFailureException::new);

        var tenant = tenantRepository.findById(family.getTenantId())
            .filter(current -> current.getStatus() == LifecycleStatus.ACTIVE)
            .orElseThrow(AuthenticationFailureException::new);

        membershipRepository.findByIdAndTenantIdAndIdentityIdAndStatus(
            family.getMembershipId(),
            family.getTenantId(),
            family.getIdentityId(),
            LifecycleStatus.ACTIVE
        ).orElseThrow(AuthenticationFailureException::new);

        var context = tenantAccessService.activate(
            identity.getExternalSubject(),
            tenant.getCode(),
            correlationId
        );

        if (!context.membershipId().value().equals(family.getMembershipId())) {
            throw new AuthenticationFailureException();
        }

        return new ResolvedSessionDetails(
            identity.getExternalSubject(),
            tenant.getCode(),
            context
        );
    }
}
