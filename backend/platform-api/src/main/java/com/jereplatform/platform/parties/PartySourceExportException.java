package com.jereplatform.platform.parties;

final class PartySourceExportException extends RuntimeException {

    enum Reason {
        AUTHENTICATION,
        CONFIGURATION,
        INVALID_ARTIFACT,
        TENANT_MISMATCH
    }

    private final Reason reason;

    PartySourceExportException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    Reason reason() {
        return reason;
    }
}
