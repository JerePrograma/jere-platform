package com.jereplatform.kernel.identity.application;

import com.jereplatform.kernel.identity.api.AuthenticationFailureException;
import com.jereplatform.kernel.identity.api.AuthenticationSessionPolicy;
import com.jereplatform.kernel.identity.api.SessionGrant;
import com.jereplatform.kernel.identity.internal.persistence.CredentialRepository;
import com.jereplatform.kernel.identity.internal.persistence.CredentialStatus;
import com.jereplatform.kernel.identity.internal.persistence.RefreshTokenEntity;
import com.jereplatform.kernel.identity.internal.persistence.RefreshTokenRepository;
import com.jereplatform.kernel.identity.internal.persistence.RefreshTokenStatus;
import com.jereplatform.kernel.identity.internal.persistence.SessionFamilyEntity;
import com.jereplatform.kernel.identity.internal.persistence.SessionFamilyRepository;
import com.jereplatform.kernel.identity.internal.security.RefreshTokenCodec;
import com.jereplatform.kernel.tenancy.api.TenantAccessDeniedException;
import com.jereplatform.kernel.tenancy.application.TenantAccessService;
import com.jereplatform.kernel.tenancy.internal.persistence.IdentityRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.LifecycleStatus;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final IdentityRepository identityRepository;
    private final CredentialRepository credentialRepository;
    private final SessionFamilyRepository sessionFamilyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantAccessService tenantAccessService;
    private final SessionContextResolver sessionContextResolver;
    private final RefreshTokenCodec refreshTokenCodec;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AuthenticationSessionPolicy policy;
    private final String dummyPasswordHash;

    public AuthenticationService(
        IdentityRepository identityRepository,
        CredentialRepository credentialRepository,
        SessionFamilyRepository sessionFamilyRepository,
        RefreshTokenRepository refreshTokenRepository,
        TenantAccessService tenantAccessService,
        SessionContextResolver sessionContextResolver,
        RefreshTokenCodec refreshTokenCodec,
        PasswordEncoder passwordEncoder,
        Clock clock,
        AuthenticationSessionPolicy policy
    ) {
        this.identityRepository = identityRepository;
        this.credentialRepository = credentialRepository;
        this.sessionFamilyRepository = sessionFamilyRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tenantAccessService = tenantAccessService;
        this.sessionContextResolver = sessionContextResolver;
        this.refreshTokenCodec = refreshTokenCodec;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.policy = policy;
        this.dummyPasswordHash = passwordEncoder.encode("invalid-credential-timing-equalizer");
    }

    @Transactional
    public SessionGrant login(
        String externalSubject,
        String rawPassword,
        String tenantCode,
        UUID correlationId
    ) {
        var normalizedSubject = normalize(externalSubject);
        var identity = identityRepository
            .findByExternalSubjectIgnoreCaseAndStatus(normalizedSubject, LifecycleStatus.ACTIVE)
            .orElse(null);
        var credential = identity == null
            ? null
            : credentialRepository
                .findByIdentityIdAndStatus(identity.getId(), CredentialStatus.ACTIVE)
                .orElse(null);

        var candidateHash = credential == null
            ? dummyPasswordHash
            : credential.getPasswordHash();
        var matches = rawPassword != null && passwordEncoder.matches(rawPassword, candidateHash);
        if (!matches || identity == null || credential == null) {
            throw new AuthenticationFailureException();
        }

        var context = activateTenant(normalizedSubject, tenantCode, correlationId);
        var now = clock.instant();
        var familyId = UUID.randomUUID();
        var refreshExpiresAt = now.plus(policy.refreshTokenLifetime());

        sessionFamilyRepository.save(new SessionFamilyEntity(
            familyId,
            identity.getId(),
            context.tenantId().value(),
            context.membershipId().value(),
            credential.getInvalidationVersion(),
            now,
            refreshExpiresAt
        ));

        var rawRefreshToken = refreshTokenCodec.generate();
        refreshTokenRepository.save(new RefreshTokenEntity(
            UUID.randomUUID(),
            familyId,
            refreshTokenCodec.hash(rawRefreshToken),
            0,
            now,
            refreshExpiresAt
        ));

        return new SessionGrant(
            normalizedSubject,
            normalize(tenantCode),
            context,
            familyId,
            credential.getInvalidationVersion(),
            rawRefreshToken,
            refreshExpiresAt
        );
    }

    @Transactional(noRollbackFor = AuthenticationFailureException.class)
    public SessionGrant refresh(String rawRefreshToken, UUID correlationId) {
        var tokenHash = hashOrFail(rawRefreshToken);
        var token = refreshTokenRepository.findLockedByTokenHash(tokenHash)
            .orElseThrow(AuthenticationFailureException::new);
        var family = sessionFamilyRepository.findLockedById(token.getFamilyId())
            .orElseThrow(AuthenticationFailureException::new);
        var now = clock.instant();

        if (token.getStatus() != RefreshTokenStatus.ACTIVE) {
            if (token.getStatus() == RefreshTokenStatus.CONSUMED) {
                family.compromise(now);
                refreshTokenRepository.revokeAllActiveByFamilyId(family.getId());
            }
            throw new AuthenticationFailureException();
        }

        if (token.isExpiredAt(now) || !family.isUsableAt(now)) {
            token.revoke();
            family.expire(now);
            refreshTokenRepository.revokeAllActiveByFamilyId(family.getId());
            throw new AuthenticationFailureException();
        }

        final ResolvedSessionDetails resolved;
        try {
            resolved = sessionContextResolver.resolve(family, now, correlationId);
        } catch (AuthenticationFailureException invalidCurrentState) {
            family.revoke(now, "CURRENT_STATE_INVALID");
            refreshTokenRepository.revokeAllActiveByFamilyId(family.getId());
            throw invalidCurrentState;
        }

        var replacementId = UUID.randomUUID();
        var replacementRawToken = refreshTokenCodec.generate();
        var replacement = new RefreshTokenEntity(
            replacementId,
            family.getId(),
            refreshTokenCodec.hash(replacementRawToken),
            token.getSequence() + 1,
            now,
            family.getExpiresAt()
        );
        refreshTokenRepository.saveAndFlush(replacement);
        token.consume(now, replacementId);
        family.touch(now);

        return new SessionGrant(
            resolved.externalSubject(),
            resolved.tenantCode(),
            resolved.tenantContext(),
            family.getId(),
            family.getCredentialVersion(),
            replacementRawToken,
            family.getExpiresAt()
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        var token = refreshTokenRepository
            .findLockedByTokenHash(refreshTokenCodec.hash(rawRefreshToken))
            .orElse(null);
        if (token == null) {
            return;
        }
        var family = sessionFamilyRepository.findLockedById(token.getFamilyId()).orElse(null);
        if (family == null) {
            return;
        }
        family.revoke(clock.instant(), "LOGOUT");
        refreshTokenRepository.revokeAllActiveByFamilyId(family.getId());
    }

    private com.jereplatform.kernel.tenancy.api.TenantContext activateTenant(
        String subject,
        String tenantCode,
        UUID correlationId
    ) {
        try {
            return tenantAccessService.activate(subject, tenantCode, correlationId);
        } catch (TenantAccessDeniedException denied) {
            throw new AuthenticationFailureException();
        }
    }

    private String hashOrFail(String rawRefreshToken) {
        try {
            return refreshTokenCodec.hash(rawRefreshToken);
        } catch (IllegalArgumentException invalid) {
            throw new AuthenticationFailureException();
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new AuthenticationFailureException();
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
