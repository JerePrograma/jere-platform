package com.jereplatform.kernel.reliability.api;

@FunctionalInterface
public interface OutboxDispatcher {

    void dispatch(OutboxMessage message) throws Exception;
}
