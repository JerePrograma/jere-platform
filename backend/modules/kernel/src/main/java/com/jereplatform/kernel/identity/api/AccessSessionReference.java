package com.jereplatform.kernel.identity.api;

import com.jereplatform.kernel.tenancy.api.IdentityId;
import com.jereplatform.kernel.tenancy.api.MembershipId;
import com.jereplatform.kernel.tenancy.api.TenantId;
import java.util.Objects;
import java.util.UUID;

public record AccessSessionReference(
    UUID sessionFamilyId,
    IdentityId identityId,
    TenantId tenantId,
    MembershipId membershipId,
    long credentialVersion
) {

    public AccessSessionReference {
        Objects.requireNonNull(sessionFamilyId, "sessionFamilyId must not be null");
        Objects.requireNonNull(identityId, "identityId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(membershipId, "membershipId must not be null");
        if (credentialVersion < 0) {
            throw new IllegalArgumentException("credentialVersion must not be negative");
        }
    }
}
