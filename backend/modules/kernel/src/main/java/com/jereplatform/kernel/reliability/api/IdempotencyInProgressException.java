package com.jereplatform.kernel.reliability.api;

public final class IdempotencyInProgressException extends RuntimeException {

    public IdempotencyInProgressException() {
        super("Equivalent request is already in progress");
    }
}
