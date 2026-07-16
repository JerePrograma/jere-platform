package com.jereplatform.kernel.identity.application;

import com.jereplatform.kernel.identity.api.AuthenticationFailureException;
import com.jereplatform.kernel.identity.internal.persistence.CredentialEntity;
import com.jereplatform.kernel.identity.internal.persistence.CredentialRepository;
import com.jereplatform.kernel.tenancy.api.IdentityId;
import com.jereplatform.kernel.tenancy.internal.persistence.IdentityRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.LifecycleStatus;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CredentialRegistrationService {

    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final IdentityRepository identityRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public CredentialRegistrationService(
        IdentityRepository identityRepository,
        CredentialRepository credentialRepository,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.identityRepository = identityRepository;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public void register(IdentityId identityId, String rawPassword) {
        requireStrongEnoughPassword(rawPassword);
        identityRepository.findById(identityId.value())
            .filter(identity -> identity.getStatus() == LifecycleStatus.ACTIVE)
            .orElseThrow(AuthenticationFailureException::new);

        if (credentialRepository.existsById(identityId.value())) {
            throw new IllegalStateException("Credentials already exist for identity");
        }

        var now = clock.instant();
        credentialRepository.save(new CredentialEntity(
            identityId.value(),
            passwordEncoder.encode(rawPassword),
            now
        ));
    }

    public void replacePassword(IdentityId identityId, String rawPassword) {
        requireStrongEnoughPassword(rawPassword);
        var credential = credentialRepository.findById(identityId.value())
            .orElseThrow(AuthenticationFailureException::new);
        credential.replacePassword(passwordEncoder.encode(rawPassword), clock.instant());
    }

    private static void requireStrongEnoughPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                "Password must contain at least " + MINIMUM_PASSWORD_LENGTH + " characters"
            );
        }
    }
}
