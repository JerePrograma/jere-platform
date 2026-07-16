package com.jereplatform.platform.identity;

import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

public record PlatformPrincipal(
    String name,
    String tenantCode,
    TenantContext tenantContext,
    UUID sessionFamilyId,
    long credentialVersion
) implements Principal {

    public PlatformPrincipal {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("tenantCode must not be blank");
        }
        Objects.requireNonNull(tenantContext, "tenantContext must not be null");
        Objects.requireNonNull(sessionFamilyId, "sessionFamilyId must not be null");
    }

    @Override
    public String getName() {
        return name;
    }
}
