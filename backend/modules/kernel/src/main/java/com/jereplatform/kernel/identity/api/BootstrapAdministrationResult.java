package com.jereplatform.kernel.identity.api;

import com.jereplatform.kernel.tenancy.api.BranchId;
import com.jereplatform.kernel.tenancy.api.IdentityId;
import com.jereplatform.kernel.tenancy.api.MembershipId;
import com.jereplatform.kernel.tenancy.api.OrganizationId;
import com.jereplatform.kernel.tenancy.api.TenantId;

public record BootstrapAdministrationResult(
    TenantId tenantId,
    OrganizationId organizationId,
    BranchId branchId,
    IdentityId identityId,
    MembershipId membershipId
) {
}
