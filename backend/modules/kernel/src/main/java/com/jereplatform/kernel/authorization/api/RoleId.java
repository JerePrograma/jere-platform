package com.jereplatform.kernel.authorization.api;

import java.util.Objects;
import java.util.UUID;

public record RoleId(UUID value) {

    public RoleId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static RoleId random() {
        return new RoleId(UUID.randomUUID());
    }
}
