package com.jereplatform.kernel.reliability.internal;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SafeMetadata {

    private static final int MAXIMUM_ENTRIES = 20;
    private static final int MAXIMUM_KEY_LENGTH = 80;
    private static final int MAXIMUM_VALUE_LENGTH = 240;
    private static final Set<String> SENSITIVE_FRAGMENTS = Set.of(
        "password", "passwd", "secret", "token", "authorization", "cookie",
        "credential", "api-key", "apikey", "private-key", "refresh"
    );

    public Map<String, String> sanitize(Map<String, String> source) {
        var result = new LinkedHashMap<String, String>();
        if (source == null) {
            return Map.of();
        }

        for (var entry : source.entrySet()) {
            if (result.size() >= MAXIMUM_ENTRIES) {
                break;
            }
            var key = normalizeKey(entry.getKey());
            if (key == null || sensitive(key)) {
                continue;
            }
            var value = entry.getValue();
            if (value == null) {
                continue;
            }
            result.put(key, truncate(value.trim(), MAXIMUM_VALUE_LENGTH));
        }
        return Map.copyOf(result);
    }

    public String failureCode(Throwable failure) {
        if (failure == null) {
            return "UNKNOWN_FAILURE";
        }
        var simpleName = failure.getClass().getSimpleName();
        if (simpleName == null || simpleName.isBlank()) {
            return "UNKNOWN_FAILURE";
        }
        var normalized = simpleName
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .replaceAll("[^A-Za-z0-9_]", "_")
            .toUpperCase(Locale.ROOT);
        return truncate(normalized, 80);
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return truncate(key.trim().toLowerCase(Locale.ROOT), MAXIMUM_KEY_LENGTH);
    }

    private static boolean sensitive(String key) {
        return SENSITIVE_FRAGMENTS.stream().anyMatch(key::contains);
    }

    private static String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }
}
