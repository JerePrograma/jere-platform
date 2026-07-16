package com.jereplatform.kernel.identity.application;

import com.jereplatform.kernel.identity.api.AccessSessionReference;
import com.jereplatform.kernel.identity.api.AuthenticationFailureException;
import com.jereplatform.kernel.identity.api.ValidatedSession;
import com.jereplatform.kernel.identity.internal.persistence.SessionFamilyRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SessionValidationService {

    private final SessionFamilyRepository sessionFamilyRepository;
    private final SessionContextResolver sessionContextResolver;
    private final Clock clock;

    public SessionValidationService(
        SessionFamilyRepository sessionFamilyRepository,
        SessionContextResolver sessionContextResolver,
        Clock clock
    ) {
        this.sessionFamilyRepository = sessionFamilyRepository;
        this.sessionContextResolver = sessionContextResolver;
        this.clock = clock;
    }

    public ValidatedSession validate(
        AccessSessionReference reference,
        UUID correlationId
    ) {
        var family = sessionFamilyRepository.findById(reference.sessionFamilyId())
            .orElseThrow(AuthenticationFailureException::new);

        if (!family.getIdentityId().equals(reference.identityId().value())
            || !family.getTenantId().equals(reference.tenantId().value())
            || !family.getMembershipId().equals(reference.membershipId().value())
            || family.getCredentialVersion() != reference.credentialVersion()) {
            throw new AuthenticationFailureException();
        }

        var resolved = sessionContextResolver.resolve(
            family,
            clock.instant(),
            correlationId
        );

        return new ValidatedSession(
            resolved.externalSubject(),
            resolved.tenantCode(),
            resolved.tenantContext(),
            family.getId(),
            family.getCredentialVersion()
        );
    }
}
