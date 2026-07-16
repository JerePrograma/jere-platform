package com.jereplatform.commercial.parties.api;

public final class PartyInactiveException extends RuntimeException {

    public PartyInactiveException() {
        super("Party reference is inactive");
    }
}
