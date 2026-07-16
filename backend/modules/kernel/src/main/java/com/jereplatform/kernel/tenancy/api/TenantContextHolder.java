package com.jereplatform.kernel.tenancy.api;

import java.util.Optional;

public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static Optional<TenantContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static TenantContext requireCurrent() {
        return current().orElseThrow(() -> new IllegalStateException("Tenant context is required"));
    }

    public static Scope open(TenantContext context) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Tenant context is already active on this thread");
        }
        CURRENT.set(context);
        return new Scope();
    }

    public static final class Scope implements AutoCloseable {

        private boolean closed;

        private Scope() {
        }

        @Override
        public void close() {
            if (!closed) {
                CURRENT.remove();
                closed = true;
            }
        }
    }
}
