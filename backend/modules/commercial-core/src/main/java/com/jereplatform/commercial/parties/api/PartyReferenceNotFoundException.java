package com.jereplatform.commercial.parties.api;

public final class PartyReferenceNotFoundException extends RuntimeException {

    public PartyReferenceNotFoundException() {
        super("Party reference not found");
    }
}
