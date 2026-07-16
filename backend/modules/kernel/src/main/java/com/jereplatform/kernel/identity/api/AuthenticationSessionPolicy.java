package com.jereplatform.kernel.identity.api;

import java.time.Duration;
import java.util.Objects;

public record AuthenticationSessionPolicy(
    Duration accessTokenLifetime,
    Duration refreshTokenLifetime
) {

    public AuthenticationSessionPolicy {
        Objects.requireNonNull(accessTokenLifetime, "accessTokenLifetime must not be null");
        Objects.requireNonNull(refreshTokenLifetime, "refreshTokenLifetime must not be null");
        if (accessTokenLifetime.isNegative() || accessTokenLifetime.isZero()) {
            throw new IllegalArgumentException("accessTokenLifetime must be positive");
        }
        if (refreshTokenLifetime.isNegative() || refreshTokenLifetime.isZero()) {
            throw new IllegalArgumentException("refreshTokenLifetime must be positive");
        }
        if (refreshTokenLifetime.compareTo(accessTokenLifetime) <= 0) {
            throw new IllegalArgumentException(
                "refreshTokenLifetime must be greater than accessTokenLifetime"
            );
        }
    }
}
