package com.jereplatform.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jereplatform.commercial.parties.api.PartySourceType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PartySourceTypeContractTest {

    private static final Pattern TYPESCRIPT_TUPLE = Pattern.compile(
        "partySourceTypes\\s*=\\s*\\[([\\s\\S]*?)]\\s*as const"
    );
    private static final Pattern QUOTED_VALUE = Pattern.compile("'([^']+)'");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void javaAndTypescriptMatchTheCanonicalSourceTypeContract() throws Exception {
        var root = findRepositoryRoot();
        var canonical = objectMapper.readValue(
            Files.readString(root.resolve("contracts/parties/source-types.json")),
            new TypeReference<List<String>>() { }
        ).stream().sorted().toList();

        var javaCodes = Arrays.stream(PartySourceType.values())
            .map(Enum::name)
            .sorted()
            .toList();

        var frontendSource = Files.readString(root.resolve(
            "frontend/apps/platform-shell/src/parties/party-reference.ts"));
        var tuple = TYPESCRIPT_TUPLE.matcher(frontendSource);
        assertThat(tuple.find()).as("partySourceTypes tuple").isTrue();
        var values = QUOTED_VALUE.matcher(tuple.group(1));
        var frontendCodes = new java.util.ArrayList<String>();
        while (values.find()) {
            frontendCodes.add(values.group(1));
        }
        frontendCodes.sort(String::compareTo);

        assertThat(javaCodes).containsExactlyElementsOf(canonical);
        assertThat(frontendCodes).containsExactlyElementsOf(canonical);
        assertThat(canonical).doesNotHaveDuplicates();
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("contracts/parties/source-types.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root not found");
    }
}
