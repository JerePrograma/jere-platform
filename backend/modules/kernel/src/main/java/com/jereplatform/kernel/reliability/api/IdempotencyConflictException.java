package com.jereplatform.kernel.reliability.api;

public final class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency key was already used with different request content");
    }
}
