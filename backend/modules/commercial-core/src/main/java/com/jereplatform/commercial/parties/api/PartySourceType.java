package com.jereplatform.commercial.parties.api;

import java.util.Arrays;
import java.util.Optional;

public enum PartySourceType {
    GESTUDIO_STUDENT,
    SCALARIS_THIRD_PARTY;

    public static Optional<PartySourceType> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        var normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
            .filter(value -> value.name().equals(normalized))
            .findFirst();
    }
}
