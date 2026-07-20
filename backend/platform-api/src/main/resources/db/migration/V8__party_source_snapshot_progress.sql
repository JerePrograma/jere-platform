CREATE TABLE platform.party_source_snapshot (
    tenant_id UUID NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    checkpoint VARCHAR(160) NOT NULL,
    page_count INTEGER NOT NULL,
    next_page INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, source_type, checkpoint),
    CONSTRAINT fk_party_source_snapshot_tenant
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_party_source_snapshot_source_type
        CHECK (source_type IN ('GESTUDIO_STUDENT', 'SCALARIS_THIRD_PARTY')),
    CONSTRAINT ck_party_source_snapshot_pages
        CHECK (page_count BETWEEN 1 AND 1000 AND next_page BETWEEN 1 AND page_count + 1),
    CONSTRAINT ck_party_source_snapshot_timestamps
        CHECK (updated_at >= created_at AND (completed_at IS NULL OR completed_at >= created_at))
);

CREATE TABLE platform.party_source_snapshot_page (
    tenant_id UUID NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    checkpoint VARCHAR(160) NOT NULL,
    page_number INTEGER NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    next_cursor VARCHAR(500),
    full_snapshot BOOLEAN NOT NULL,
    record_count INTEGER NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, source_type, checkpoint, page_number),
    CONSTRAINT fk_party_source_snapshot_page
        FOREIGN KEY (tenant_id, source_type, checkpoint)
        REFERENCES platform.party_source_snapshot (tenant_id, source_type, checkpoint)
        ON DELETE CASCADE,
    CONSTRAINT ck_party_source_snapshot_page_number
        CHECK (page_number BETWEEN 1 AND 1000),
    CONSTRAINT ck_party_source_snapshot_page_hash
        CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_party_source_snapshot_page_records
        CHECK (record_count BETWEEN 0 AND 1000)
);

CREATE TABLE platform.party_source_snapshot_record (
    tenant_id UUID NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    checkpoint VARCHAR(160) NOT NULL,
    page_number INTEGER NOT NULL,
    source_id VARCHAR(160) NOT NULL,
    PRIMARY KEY (tenant_id, source_type, checkpoint, source_id),
    CONSTRAINT fk_party_source_snapshot_record_page
        FOREIGN KEY (tenant_id, source_type, checkpoint, page_number)
        REFERENCES platform.party_source_snapshot_page (
            tenant_id, source_type, checkpoint, page_number
        )
        ON DELETE CASCADE,
    CONSTRAINT ck_party_source_snapshot_record_id
        CHECK (length(trim(source_id)) > 0)
);

CREATE INDEX idx_party_source_snapshot_incomplete
    ON platform.party_source_snapshot (updated_at)
    WHERE completed_at IS NULL;

CREATE INDEX idx_party_source_snapshot_record_page
    ON platform.party_source_snapshot_record (
        tenant_id, source_type, checkpoint, page_number
    );
