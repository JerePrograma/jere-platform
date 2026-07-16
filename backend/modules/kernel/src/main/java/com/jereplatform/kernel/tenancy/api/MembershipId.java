package com.jereplatform.kernel.tenancy.api;

import java.util.Objects;
import java.util.UUID;

public record MembershipId(UUID value) {

    public MembershipId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static MembershipId random() {
        return new MembershipId(UUID.randomUUID());
    }
}
