package com.jereplatform.commercial;

import com.jereplatform.kernel.KernelModule;

/**
 * Marker for reusable commercial capabilities. The explicit reference proves
 * the allowed dependency direction from commercial core to kernel.
 */
public final class CommercialCoreModule {

    private static final Class<?> KERNEL_BOUNDARY = KernelModule.class;

    private CommercialCoreModule() {
    }

    public static Class<?> kernelBoundary() {
        return KERNEL_BOUNDARY;
    }
}
