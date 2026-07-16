package com.jereplatform.kernel.tenancy.api;

public final class TenantConflictException extends RuntimeException {

    public TenantConflictException(String message) {
        super(message);
    }
}
