package com.jereplatform.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartySourceExportContractTest {

    private static final Set<String> ROOT_FIELDS = Set.of(
        "contractVersion",
        "tenantId",
        "sourceType",
        "checkpoint",
        "nextCursor",
        "fullSnapshot",
        "records"
    );
    private static final Set<String> RECORD_FIELDS = Set.of(
        "sourceId",
        "displayName",
        "active"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void schemaAndFixturesExposeOnlyApprovedReferenceFields() throws Exception {
        var root = findRepositoryRoot();
        var schema = objectMapper.readTree(Files.readString(root.resolve(
            "contracts/parties/source-export-v1.schema.json")));

        assertThat(schema.path("additionalProperties").booleanValue()).isFalse();
        assertThat(fieldNames(schema.path("properties"))).containsExactlyInAnyOrderElementsOf(
            ROOT_FIELDS);
        var recordSchema = schema.path("properties").path("records").path("items");
        assertThat(recordSchema.path("additionalProperties").booleanValue()).isFalse();
        assertThat(fieldNames(recordSchema.path("properties")))
            .containsExactlyInAnyOrderElementsOf(RECORD_FIELDS);
        assertThat(schema.path("properties").path("contractVersion").path("const").intValue())
            .isEqualTo(1);
        assertThat(schema.path("properties").path("records").path("maxItems").intValue())
            .isEqualTo(1_000);
        assertThat(schema.path("allOf").path(0).path("then")
            .path("properties").path("nextCursor").path("type").textValue())
            .isEqualTo("null");

        assertFixture(
            root.resolve("contracts/parties/fixtures/gestudio-students-v1.json"),
            "GESTUDIO_STUDENT"
        );
        assertFixture(
            root.resolve("contracts/parties/fixtures/scalaris-third-parties-v1.json"),
            "SCALARIS_THIRD_PARTY"
        );
    }

    private void assertFixture(Path path, String expectedSourceType) throws Exception {
        var fixture = objectMapper.readTree(Files.readString(path));
        assertThat(fieldNames(fixture)).containsExactlyInAnyOrderElementsOf(ROOT_FIELDS);
        assertThat(fixture.path("contractVersion").intValue()).isEqualTo(1);
        assertThat(fixture.path("sourceType").textValue()).isEqualTo(expectedSourceType);
        assertThatCode(() -> UUID.fromString(fixture.path("tenantId").textValue()))
            .doesNotThrowAnyException();
        for (var record : fixture.path("records")) {
            assertThat(fieldNames(record)).containsExactlyInAnyOrderElementsOf(RECORD_FIELDS);
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        var names = new java.util.HashSet<String>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("contracts/parties/source-export-v1.schema.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root not found");
    }
}
