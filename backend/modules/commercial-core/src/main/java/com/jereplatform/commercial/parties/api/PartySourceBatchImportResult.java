package com.jereplatform.commercial.parties.api;

public record PartySourceBatchImportResult(
    int totalRecords,
    int createdRecords,
    int updatedRecords,
    int unchangedRecords
) {

    public PartySourceBatchImportResult {
        if (totalRecords < 0 || createdRecords < 0 || updatedRecords < 0 || unchangedRecords < 0) {
            throw new IllegalArgumentException("Import counts must not be negative");
        }
        if (totalRecords != createdRecords + updatedRecords + unchangedRecords) {
            throw new IllegalArgumentException("Import counts must equal totalRecords");
        }
    }
}
