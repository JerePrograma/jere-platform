package com.jereplatform.platform.parties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jereplatform.kernel.tenancy.api.IdentityId;
import com.jereplatform.kernel.tenancy.api.MembershipId;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import com.jereplatform.kernel.tenancy.api.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class SignedPartySourceExportReaderTest {

    @Test
    void previousSecretIsAcceptedOnlyDuringTheConfiguredRotationWindow() throws Exception {
        String currentSecret = runtimeSecret();
        String previousSecret = runtimeSecret();
        UUID tenantId = UUID.randomUUID();
        byte[] body = ("""
            {"contractVersion":1,"tenantId":"%s","sourceType":"GESTUDIO_STUDENT",\
"checkpoint":"rotation-checkpoint","nextCursor":null,"pageNumber":1,\
"pageCount":1,"fullSnapshot":true,"records":[]}
            """).formatted(tenantId).strip().getBytes(StandardCharsets.UTF_8);
        String oldSignature = signature(previousSecret, body);
        TenantContext context = new TenantContext(
            new TenantId(tenantId),
            new IdentityId(UUID.randomUUID()),
            new MembershipId(UUID.randomUUID()),
            Set.of(),
            UUID.randomUUID()
        );

        var overlapReader = reader(currentSecret, previousSecret);
        assertThat(overlapReader.read(
            context, "GESTUDIO_STUDENT", oldSignature, body).tenantId()).isEqualTo(tenantId);

        var retiredReader = reader(currentSecret, null);
        assertThatThrownBy(() -> retiredReader.read(
            context, "GESTUDIO_STUDENT", oldSignature, body))
            .isInstanceOf(PartySourceExportException.class)
            .extracting(failure -> ((PartySourceExportException) failure).reason())
            .isEqualTo(PartySourceExportException.Reason.AUTHENTICATION);
    }

    private static SignedPartySourceExportReader reader(String current, String previous) {
        return new SignedPartySourceExportReader(
            new PartySourceExportProperties(current, previous, null, null),
            new ObjectMapper()
        );
    }

    private static String runtimeSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String signature(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
