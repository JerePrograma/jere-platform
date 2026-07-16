INSERT INTO platform.permission_catalog (code, module_code, display_name, branch_scoped) VALUES
    ('commercial.parties.read', 'platform', 'Read commercial party references', FALSE),
    ('commercial.parties.manage', 'platform', 'Import and reconcile commercial party references', FALSE);

UPDATE platform.base_role_template
   SET template_version = CASE system_key
       WHEN 'OWNER' THEN 3
       WHEN 'OPERATOR' THEN 2
       WHEN 'VIEWER' THEN 2
       ELSE template_version
   END
 WHERE system_key IN ('OWNER', 'OPERATOR', 'VIEWER');

INSERT INTO platform.base_role_template_permission (system_key, permission_code) VALUES
    ('OWNER', 'commercial.parties.read'),
    ('OWNER', 'commercial.parties.manage'),
    ('OPERATOR', 'commercial.parties.read'),
    ('VIEWER', 'commercial.parties.read');

CREATE TABLE platform.party_reference (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id VARCHAR(160) NOT NULL,
    current_display_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_party_reference_tenant
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_party_reference_tenant_id
        UNIQUE (tenant_id, id),
    CONSTRAINT uq_party_reference_source
        UNIQUE (tenant_id, source_type, source_id),
    CONSTRAINT ck_party_reference_source_type
        CHECK (source_type IN ('GESTUDIO_STUDENT', 'SCALARIS_THIRD_PARTY')),
    CONSTRAINT ck_party_reference_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_party_reference_source_id
        CHECK (length(trim(source_id)) > 0),
    CONSTRAINT ck_party_reference_display_name
        CHECK (length(trim(current_display_name)) > 0),
    CONSTRAINT ck_party_reference_timestamps
        CHECK (updated_at >= created_at)
);

CREATE INDEX idx_party_reference_tenant_status_name
    ON platform.party_reference (tenant_id, status, current_display_name);

CREATE INDEX idx_party_reference_tenant_source
    ON platform.party_reference (tenant_id, source_type, source_id);
