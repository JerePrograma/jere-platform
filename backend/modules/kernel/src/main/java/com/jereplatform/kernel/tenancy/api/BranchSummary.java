package com.jereplatform.kernel.tenancy.api;

import java.util.Objects;

public record BranchSummary(
    BranchId id,
    OrganizationId organizationId,
    String code,
    String displayName
) {

    public BranchSummary {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        code = requireText(code, "code");
        displayName = requireText(displayName, "displayName");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
