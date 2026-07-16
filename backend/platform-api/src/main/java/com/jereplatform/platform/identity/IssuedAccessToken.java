package com.jereplatform.platform.identity;

import java.time.Instant;
import java.util.Objects;

public record IssuedAccessToken(String value, Instant expiresAt) {

    public IssuedAccessToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
