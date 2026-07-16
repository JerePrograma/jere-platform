package com.jereplatform.kernel.tenancy.api;

import java.util.Objects;
import java.util.UUID;

public record BranchId(UUID value) {

    public BranchId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static BranchId random() {
        return new BranchId(UUID.randomUUID());
    }
}
