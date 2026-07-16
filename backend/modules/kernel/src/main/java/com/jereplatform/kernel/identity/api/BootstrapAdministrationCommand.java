package com.jereplatform.kernel.identity.api;

public record BootstrapAdministrationCommand(
    String tenantCode,
    String tenantDisplayName,
    String organizationCode,
    String organizationDisplayName,
    String branchCode,
    String branchDisplayName,
    String externalSubject,
    String password
) {
}
