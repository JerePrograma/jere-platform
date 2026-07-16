package com.jereplatform.kernel.identity.api;

import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.util.Objects;
import java.util.UUID;

public record ValidatedSession(
    String externalSubject,
    String tenantCode,
    TenantContext tenantContext,
    UUID sessionFamilyId,
    long credentialVersion
) {

    public ValidatedSession {
        externalSubject = requireText(externalSubject, "externalSubject");
        tenantCode = requireText(tenantCode, "tenantCode");
        Objects.requireNonNull(tenantContext, "tenantContext must not be null");
        Objects.requireNonNull(sessionFamilyId, "sessionFamilyId must not be null");
        if (credentialVersion < 0) {
            throw new IllegalArgumentException("credentialVersion must not be negative");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
