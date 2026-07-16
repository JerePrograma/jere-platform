package com.jereplatform.kernel.identity.api;

public final class AuthenticationFailureException extends RuntimeException {

    public AuthenticationFailureException() {
        super("Authentication failed");
    }
}
