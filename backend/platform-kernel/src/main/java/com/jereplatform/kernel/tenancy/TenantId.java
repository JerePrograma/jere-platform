package com.jereplatform.kernel.tenancy;

import java.util.Objects;
import java.util.UUID;

/** Stable tenant identifier shared by platform modules. */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static TenantId random() {
        return new TenantId(UUID.randomUUID());
    }

    public static TenantId parse(String value) {
        return new TenantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
