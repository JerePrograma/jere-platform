package com.jereplatform.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.jereplatform.commercial.parties.api.PartyLifecycleStatus;
import com.jereplatform.commercial.parties.api.PartySourceAdapter;
import com.jereplatform.commercial.parties.api.PartySourceRecord;
import org.junit.jupiter.api.Test;

class PartySourceAdapterContractTest {

    @Test
    void gestudioStudentAdapterProducesOnlyTheSharedReferenceContract() {
        assertContract(new GestudioStudentFixtureAdapter(), "42", "Ada Lovelace");
    }

    @Test
    void scalarisThirdPartyAdapterProducesOnlyTheSharedReferenceContract() {
        assertContract(
            new ScalarisThirdPartyFixtureAdapter(),
            "7c86ae16-8432-4886-8da1-a66eff3f8db8",
            "Maderas del Sur SRL"
        );
    }

    private static void assertContract(
        PartySourceAdapter adapter,
        String sourceId,
        String expectedDisplayName
    ) {
        var record = adapter.load(sourceId);

        assertThat(record.sourceType()).isEqualTo(adapter.sourceType());
        assertThat(record.sourceId()).isEqualTo(sourceId);
        assertThat(record.displayName()).isEqualTo(expectedDisplayName);
        assertThat(record.status()).isEqualTo(PartyLifecycleStatus.ACTIVE);
        assertThat(record.getClass().getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("sourceType", "sourceId", "displayName", "status");
    }

    private static final class GestudioStudentFixtureAdapter implements PartySourceAdapter {

        @Override
        public String sourceType() {
            return "GESTUDIO_STUDENT";
        }

        @Override
        public PartySourceRecord load(String sourceId) {
            return new PartySourceRecord(
                sourceType(),
                sourceId,
                "Ada Lovelace",
                PartyLifecycleStatus.ACTIVE
            );
        }
    }

    private static final class ScalarisThirdPartyFixtureAdapter implements PartySourceAdapter {

        @Override
        public String sourceType() {
            return "SCALARIS_THIRD_PARTY";
        }

        @Override
        public PartySourceRecord load(String sourceId) {
            return new PartySourceRecord(
                sourceType(),
                sourceId,
                "Maderas del Sur SRL",
                PartyLifecycleStatus.ACTIVE
            );
        }
    }
}
