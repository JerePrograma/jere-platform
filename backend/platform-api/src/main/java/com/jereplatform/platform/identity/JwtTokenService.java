package com.jereplatform.platform.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.jereplatform.kernel.identity.api.AccessSessionReference;
import com.jereplatform.kernel.identity.api.AuthenticationFailureException;
import com.jereplatform.kernel.identity.api.AuthenticationSessionPolicy;
import com.jereplatform.kernel.identity.api.SessionGrant;
import com.jereplatform.kernel.tenancy.api.IdentityId;
import com.jereplatform.kernel.tenancy.api.MembershipId;
import com.jereplatform.kernel.tenancy.api.TenantId;
import java.time.Clock;
import java.util.UUID;

public class JwtTokenService {

    private static final String ISSUER = "jere-platform";

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Clock clock;
    private final AuthenticationSessionPolicy policy;

    public JwtTokenService(
        byte[] secret,
        Clock clock,
        AuthenticationSessionPolicy policy
    ) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
        this.clock = clock;
        this.policy = policy;
    }

    public IssuedAccessToken issue(SessionGrant grant) {
        var issuedAt = clock.instant();
        var expiresAt = issuedAt.plus(policy.accessTokenLifetime());
        var context = grant.tenantContext();

        var token = JWT.create()
            .withIssuer(ISSUER)
            .withSubject(grant.externalSubject())
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(issuedAt)
            .withExpiresAt(expiresAt)
            .withClaim("sid", grant.sessionFamilyId().toString())
            .withClaim("iid", context.identityId().value().toString())
            .withClaim("tid", context.tenantId().value().toString())
            .withClaim("mid", context.membershipId().value().toString())
            .withClaim("cv", grant.credentialVersion())
            .sign(algorithm);

        return new IssuedAccessToken(token, expiresAt);
    }

    public AccessSessionReference verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthenticationFailureException();
        }
        try {
            var decoded = verifier.verify(rawToken);
            if (decoded.getExpiresAtAsInstant() == null
                || !decoded.getExpiresAtAsInstant().isAfter(clock.instant())) {
                throw new AuthenticationFailureException();
            }
            return new AccessSessionReference(
                UUID.fromString(requiredClaim(decoded.getClaim("sid").asString())),
                new IdentityId(UUID.fromString(requiredClaim(decoded.getClaim("iid").asString()))),
                new TenantId(UUID.fromString(requiredClaim(decoded.getClaim("tid").asString()))),
                new MembershipId(UUID.fromString(requiredClaim(decoded.getClaim("mid").asString()))),
                decoded.getClaim("cv").asLong()
            );
        } catch (JWTVerificationException | IllegalArgumentException | NullPointerException invalid) {
            throw new AuthenticationFailureException();
        }
    }

    private static String requiredClaim(String value) {
        if (value == null || value.isBlank()) {
            throw new AuthenticationFailureException();
        }
        return value;
    }
}
