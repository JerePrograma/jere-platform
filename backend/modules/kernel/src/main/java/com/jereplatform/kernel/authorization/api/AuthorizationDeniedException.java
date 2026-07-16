package com.jereplatform.kernel.authorization.api;

public final class AuthorizationDeniedException extends RuntimeException {

    public AuthorizationDeniedException() {
        super("Authorization denied");
    }
}
