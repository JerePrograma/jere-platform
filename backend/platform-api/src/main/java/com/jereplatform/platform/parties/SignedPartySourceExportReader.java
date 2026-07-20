package com.jereplatform.platform.parties;

import static com.jereplatform.platform.parties.PartySourceExportException.Reason.AUTHENTICATION;
import static com.jereplatform.platform.parties.PartySourceExportException.Reason.CONFIGURATION;
import static com.jereplatform.platform.parties.PartySourceExportException.Reason.INVALID_ARTIFACT;
import static com.jereplatform.platform.parties.PartySourceExportException.Reason.TENANT_MISMATCH;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.jereplatform.commercial.parties.api.PartySourceType;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class SignedPartySourceExportReader {

    static final int MAX_BODY_BYTES = 1_000_000;
    private static final int MAX_RECORDS = 1_000;
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final PartySourceExportProperties properties;
    private final ObjectReader objectReader;

    SignedPartySourceExportReader(
        PartySourceExportProperties properties,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectReader = objectMapper.readerFor(PartySourceExport.class)
            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    PartySourceExport read(
        TenantContext context,
        String declaredSourceType,
        String signature,
        byte[] body
    ) {
        if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
            throw new PartySourceExportException(INVALID_ARTIFACT);
        }
        var sourceType = PartySourceType.fromCode(declaredSourceType)
            .orElseThrow(() -> new PartySourceExportException(INVALID_ARTIFACT));
        verifySignature(sourceType, signature, body);

        PartySourceExport parsed;
        try {
            parsed = objectReader.readValue(body);
        } catch (IOException failure) {
            throw new PartySourceExportException(INVALID_ARTIFACT);
        }
        if (parsed.contractVersion() != 1
            || parsed.tenantId() == null
            || !sourceType.name().equals(parsed.sourceType())
            || parsed.records() == null
            || parsed.records().size() > MAX_RECORDS
            || parsed.records().stream().anyMatch(record -> record == null)) {
            throw new PartySourceExportException(INVALID_ARTIFACT);
        }
        if (!context.tenantId().value().equals(parsed.tenantId())) {
            throw new PartySourceExportException(TENANT_MISMATCH);
        }

        var checkpoint = requireText(parsed.checkpoint(), 160);
        var nextCursor = parsed.nextCursor() == null
            ? null
            : requireText(parsed.nextCursor(), 500);
        if (parsed.fullSnapshot() && nextCursor != null) {
            throw new PartySourceExportException(INVALID_ARTIFACT);
        }
        return new PartySourceExport(
            1,
            parsed.tenantId(),
            sourceType.name(),
            checkpoint,
            nextCursor,
            parsed.fullSnapshot(),
            List.copyOf(parsed.records())
        );
    }

    private void verifySignature(PartySourceType sourceType, String signature, byte[] body) {
        var provided = decodeSignature(signature);
        var configured = properties.secretsFor(sourceType).stream()
            .filter(secret -> secret != null && !secret.isBlank())
            .toList();
        if (configured.isEmpty()) {
            throw new PartySourceExportException(CONFIGURATION);
        }

        for (var secret : configured) {
            var key = secret.getBytes(StandardCharsets.UTF_8);
            if (key.length < 32) {
                throw new PartySourceExportException(CONFIGURATION);
            }
            if (MessageDigest.isEqual(hmac(key, body), provided)) {
                return;
            }
        }
        throw new PartySourceExportException(AUTHENTICATION);
    }

    private static byte[] decodeSignature(String signature) {
        if (signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
            throw new PartySourceExportException(AUTHENTICATION);
        }
        try {
            var decoded = HexFormat.of().parseHex(signature.substring(SIGNATURE_PREFIX.length()));
            if (decoded.length != 32) {
                throw new PartySourceExportException(AUTHENTICATION);
            }
            return decoded;
        } catch (IllegalArgumentException invalidHex) {
            throw new PartySourceExportException(AUTHENTICATION);
        }
    }

    private static byte[] hmac(byte[] key, byte[] body) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("HmacSHA256 is unavailable", unavailable);
        }
    }

    private static String requireText(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new PartySourceExportException(INVALID_ARTIFACT);
        }
        var normalized = value.trim();
        if (!normalized.equals(value) || normalized.length() > maximumLength) {
            throw new PartySourceExportException(INVALID_ARTIFACT);
        }
        return normalized;
    }
}
