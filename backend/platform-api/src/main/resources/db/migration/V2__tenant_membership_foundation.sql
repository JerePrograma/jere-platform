CREATE TABLE platform.tenant (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_tenant_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_tenant_code_ci
    ON platform.tenant (LOWER(code));

CREATE TABLE platform.identity (
    id UUID PRIMARY KEY,
    external_subject VARCHAR(190) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_identity_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_identity_external_subject_ci
    ON platform.identity (LOWER(external_subject));

CREATE TABLE platform.organization (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_organization_tenant
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT uq_organization_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_organization_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_organization_tenant_code_ci
    ON platform.organization (tenant_id, LOWER(code));

CREATE INDEX idx_organization_tenant_status
    ON platform.organization (tenant_id, status);

CREATE TABLE platform.branch (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_branch_organization_tenant
        FOREIGN KEY (tenant_id, organization_id)
        REFERENCES platform.organization (tenant_id, id),
    CONSTRAINT uq_branch_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_branch_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_branch_organization_code_ci
    ON platform.branch (tenant_id, organization_id, LOWER(code));

CREATE INDEX idx_branch_tenant_status
    ON platform.branch (tenant_id, status);

CREATE TABLE platform.membership (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    identity_id UUID NOT NULL,
    default_organization_id UUID,
    status VARCHAR(30) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_membership_tenant
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT fk_membership_identity
        FOREIGN KEY (identity_id) REFERENCES platform.identity (id),
    CONSTRAINT fk_membership_default_organization
        FOREIGN KEY (tenant_id, default_organization_id)
        REFERENCES platform.organization (tenant_id, id),
    CONSTRAINT uq_membership_tenant_identity UNIQUE (tenant_id, identity_id),
    CONSTRAINT uq_membership_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_membership_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_membership_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);

CREATE INDEX idx_membership_identity_status
    ON platform.membership (identity_id, status);

CREATE INDEX idx_membership_tenant_status
    ON platform.membership (tenant_id, status);

CREATE TABLE platform.membership_branch (
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, membership_id, branch_id),
    CONSTRAINT fk_membership_branch_membership
        FOREIGN KEY (tenant_id, membership_id)
        REFERENCES platform.membership (tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_membership_branch_branch
        FOREIGN KEY (tenant_id, branch_id)
        REFERENCES platform.branch (tenant_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_membership_branch_branch
    ON platform.membership_branch (tenant_id, branch_id);
