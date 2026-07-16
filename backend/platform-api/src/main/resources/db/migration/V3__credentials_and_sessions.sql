ALTER TABLE platform.bootstrap_marker
    ADD COLUMN identity_bootstrapped_at TIMESTAMPTZ;

ALTER TABLE platform.membership
    ADD CONSTRAINT uq_membership_tenant_id_identity
    UNIQUE (tenant_id, id, identity_id);

CREATE TABLE platform.credential (
    identity_id UUID PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    invalidation_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_credential_identity
        FOREIGN KEY (identity_id) REFERENCES platform.identity (id),
    CONSTRAINT ck_credential_status
        CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT ck_credential_invalidation_version
        CHECK (invalidation_version >= 0)
);

CREATE TABLE platform.session_family (
    id UUID PRIMARY KEY,
    identity_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    credential_version BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(80),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_session_family_credential
        FOREIGN KEY (identity_id) REFERENCES platform.credential (identity_id),
    CONSTRAINT fk_session_family_membership
        FOREIGN KEY (tenant_id, membership_id, identity_id)
        REFERENCES platform.membership (tenant_id, id, identity_id),
    CONSTRAINT ck_session_family_status
        CHECK (status IN ('ACTIVE', 'REVOKED', 'COMPROMISED', 'EXPIRED')),
    CONSTRAINT ck_session_family_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT ck_session_family_revocation
        CHECK (
            (status = 'ACTIVE' AND revoked_at IS NULL AND revocation_reason IS NULL)
            OR (status <> 'ACTIVE' AND revoked_at IS NOT NULL AND revocation_reason IS NOT NULL)
        ),
    CONSTRAINT ck_session_family_credential_version
        CHECK (credential_version >= 0)
);

CREATE INDEX idx_session_family_identity_status
    ON platform.session_family (identity_id, status);

CREATE INDEX idx_session_family_membership_status
    ON platform.session_family (tenant_id, membership_id, status);

CREATE TABLE platform.refresh_token (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    sequence BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    replaced_by_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refresh_token_family
        FOREIGN KEY (family_id) REFERENCES platform.session_family (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_refresh_token_replacement
        FOREIGN KEY (replaced_by_id) REFERENCES platform.refresh_token (id),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT uq_refresh_token_family_sequence UNIQUE (family_id, sequence),
    CONSTRAINT ck_refresh_token_status
        CHECK (status IN ('ACTIVE', 'CONSUMED', 'REVOKED')),
    CONSTRAINT ck_refresh_token_expiry
        CHECK (expires_at > issued_at),
    CONSTRAINT ck_refresh_token_consumption
        CHECK (
            (status = 'ACTIVE' AND consumed_at IS NULL AND replaced_by_id IS NULL)
            OR (status = 'CONSUMED' AND consumed_at IS NOT NULL AND replaced_by_id IS NOT NULL)
            OR (status = 'REVOKED')
        ),
    CONSTRAINT ck_refresh_token_sequence CHECK (sequence >= 0)
);

CREATE INDEX idx_refresh_token_family_status
    ON platform.refresh_token (family_id, status);
