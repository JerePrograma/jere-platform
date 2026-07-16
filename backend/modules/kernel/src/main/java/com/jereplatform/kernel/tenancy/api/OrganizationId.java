package com.jereplatform.kernel.tenancy.api;

import java.util.Objects;
import java.util.UUID;

public record OrganizationId(UUID value) {

    public OrganizationId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static OrganizationId random() {
        return new OrganizationId(UUID.randomUUID());
    }
}
