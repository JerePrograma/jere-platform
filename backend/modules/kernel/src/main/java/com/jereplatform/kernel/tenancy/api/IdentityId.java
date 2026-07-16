package com.jereplatform.kernel.tenancy.api;

import java.util.Objects;
import java.util.UUID;

public record IdentityId(UUID value) {

    public IdentityId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static IdentityId random() {
        return new IdentityId(UUID.randomUUID());
    }
}
