package com.jereplatform.commercial.parties.api;

public interface PartySourceAdapter {

    String sourceType();

    PartySourceRecord load(String sourceId);
}
