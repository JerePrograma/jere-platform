package com.jereplatform.kernel.reliability.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class ReliabilityHashing {

    public String hashText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value must not be blank");
        }
        return hashBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    public String hashBytes(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value)
            );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
