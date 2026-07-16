package com.jereplatform.academy;

/** Marker for the academy bounded context. Business behavior is migrated later. */
public final class AcademyModule {

    private AcademyModule() {
    }

    public static String moduleName() {
        return "academy";
    }
}
