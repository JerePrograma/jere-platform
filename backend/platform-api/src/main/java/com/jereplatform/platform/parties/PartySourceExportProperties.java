package com.jereplatform.platform.parties;

import com.jereplatform.commercial.parties.api.PartySourceType;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.party-sources")
public record PartySourceExportProperties(
    String gestudioCurrentSecret,
    String gestudioPreviousSecret,
    String scalarisCurrentSecret,
    String scalarisPreviousSecret
) {

    List<String> secretsFor(PartySourceType sourceType) {
        return switch (sourceType) {
            case GESTUDIO_STUDENT -> Arrays.asList(
                gestudioCurrentSecret,
                gestudioPreviousSecret
            );
            case SCALARIS_THIRD_PARTY -> Arrays.asList(
                scalarisCurrentSecret,
                scalarisPreviousSecret
            );
        };
    }
}
