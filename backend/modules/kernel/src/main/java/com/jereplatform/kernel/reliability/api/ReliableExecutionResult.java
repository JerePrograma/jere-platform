package com.jereplatform.kernel.reliability.api;

public record ReliableExecutionResult<T>(T value, boolean replayed) {
}
