package com.jereplatform.kernel.identity.application;

import com.jereplatform.kernel.tenancy.api.TenantContext;

record ResolvedSessionDetails(
    String externalSubject,
    String tenantCode,
    TenantContext tenantContext
) {
}
