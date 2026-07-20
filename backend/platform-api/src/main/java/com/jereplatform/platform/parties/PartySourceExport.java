package com.jereplatform.platform.parties;

import java.util.List;
import java.util.UUID;

public record PartySourceExport(
    int contractVersion,
    UUID tenantId,
    String sourceType,
    String checkpoint,
    String nextCursor,
    boolean fullSnapshot,
    List<SourceRecord> records
) {

    public PartySourceExport {
        if (records != null) {
            records = List.copyOf(records);
        }
    }

    public record SourceRecord(
        String sourceId,
        String displayName,
        Boolean active
    ) {
    }
}
