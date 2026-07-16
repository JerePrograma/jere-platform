package com.jereplatform.verticals;

import com.jereplatform.commercial.CommercialCoreModule;
import com.jereplatform.kernel.KernelModule;

/**
 * Marker for product-specific vertical modules.
 */
public final class VerticalsModule {

    private static final Class<?>[] ALLOWED_FOUNDATIONS = {
        KernelModule.class,
        CommercialCoreModule.class
    };

    private VerticalsModule() {
    }

    public static Class<?>[] allowedFoundations() {
        return ALLOWED_FOUNDATIONS.clone();
    }
}
