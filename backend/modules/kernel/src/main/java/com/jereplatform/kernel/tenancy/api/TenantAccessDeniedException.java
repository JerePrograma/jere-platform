package com.jereplatform.kernel.tenancy.api;

public final class TenantAccessDeniedException extends RuntimeException {

    public TenantAccessDeniedException(String message) {
        super(message);
    }
}
