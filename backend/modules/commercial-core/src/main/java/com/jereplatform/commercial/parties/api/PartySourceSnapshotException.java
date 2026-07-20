package com.jereplatform.commercial.parties.api;

public final class PartySourceSnapshotException extends RuntimeException {

    public enum Reason {
        SNAPSHOT_NOT_FOUND,
        SNAPSHOT_NOT_COMPLETE,
        PAGE_OUT_OF_ORDER,
        PAGE_CONFLICT
    }

    private final Reason reason;

    public PartySourceSnapshotException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
