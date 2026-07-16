package com.jereplatform.commercial.parties;

import com.jereplatform.kernel.tenancy.TenantId;
import java.util.Objects;
import java.util.UUID;

/** Cross-module reference to a customer without exposing persistence entities. */
public record CustomerReference(TenantId tenantId, UUID customerId) {

    public CustomerReference {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
    }
}
