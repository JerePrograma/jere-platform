CREATE TABLE platform.module_catalog (
    code VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE platform.permission_catalog (
    code VARCHAR(120) PRIMARY KEY,
    module_code VARCHAR(40) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    branch_scoped BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_permission_module
        FOREIGN KEY (module_code) REFERENCES platform.module_catalog (code)
);

CREATE INDEX idx_permission_module_active
    ON platform.permission_catalog (module_code, active);

CREATE TABLE platform.base_role_template (
    system_key VARCHAR(40) PRIMARY KEY,
    role_code VARCHAR(80) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    template_version INTEGER NOT NULL,
    CONSTRAINT ck_base_role_template_version CHECK (template_version > 0)
);

CREATE TABLE platform.base_role_template_permission (
    system_key VARCHAR(40) NOT NULL,
    permission_code VARCHAR(120) NOT NULL,
    PRIMARY KEY (system_key, permission_code),
    CONSTRAINT fk_base_role_permission_template
        FOREIGN KEY (system_key) REFERENCES platform.base_role_template (system_key)
        ON DELETE CASCADE,
    CONSTRAINT fk_base_role_permission_catalog
        FOREIGN KEY (permission_code) REFERENCES platform.permission_catalog (code)
);

CREATE TABLE platform.tenant_role (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    system_key VARCHAR(40),
    managed_template_version INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_tenant_role_tenant
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT uq_tenant_role_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_tenant_role_managed CHECK (
        (system_key IS NULL AND managed_template_version IS NULL)
        OR (system_key IS NOT NULL AND managed_template_version IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_tenant_role_code_ci
    ON platform.tenant_role (tenant_id, LOWER(code));

CREATE UNIQUE INDEX uk_tenant_role_system_key
    ON platform.tenant_role (tenant_id, system_key)
    WHERE system_key IS NOT NULL;

CREATE TABLE platform.tenant_role_permission (
    tenant_id UUID NOT NULL,
    role_id UUID NOT NULL,
    permission_code VARCHAR(120) NOT NULL,
    PRIMARY KEY (tenant_id, role_id, permission_code),
    CONSTRAINT fk_tenant_role_permission_role
        FOREIGN KEY (tenant_id, role_id)
        REFERENCES platform.tenant_role (tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_tenant_role_permission_catalog
        FOREIGN KEY (permission_code) REFERENCES platform.permission_catalog (code)
);

CREATE TABLE platform.membership_role_assignment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    role_id UUID NOT NULL,
    branch_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_membership_role_membership
        FOREIGN KEY (tenant_id, membership_id)
        REFERENCES platform.membership (tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_membership_role_role
        FOREIGN KEY (tenant_id, role_id)
        REFERENCES platform.tenant_role (tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_membership_role_branch
        FOREIGN KEY (tenant_id, branch_id)
        REFERENCES platform.branch (tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT uq_membership_role_scope
        UNIQUE NULLS NOT DISTINCT (tenant_id, membership_id, role_id, branch_id)
);

CREATE INDEX idx_membership_role_lookup
    ON platform.membership_role_assignment (tenant_id, membership_id, branch_id);

CREATE TABLE platform.tenant_entitlement (
    tenant_id UUID NOT NULL,
    module_code VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    source VARCHAR(30) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, module_code),
    CONSTRAINT fk_entitlement_tenant
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_entitlement_module
        FOREIGN KEY (module_code) REFERENCES platform.module_catalog (code),
    CONSTRAINT ck_entitlement_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED')),
    CONSTRAINT ck_entitlement_source
        CHECK (source IN ('SYSTEM', 'PLAN', 'ADDON', 'MANUAL')),
    CONSTRAINT ck_entitlement_window
        CHECK (valid_until IS NULL OR valid_until > valid_from)
);

CREATE INDEX idx_entitlement_status_window
    ON platform.tenant_entitlement (tenant_id, status, valid_until);

CREATE TABLE platform.feature_flag_catalog (
    code VARCHAR(100) PRIMARY KEY,
    module_code VARCHAR(40) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    CONSTRAINT fk_feature_flag_module
        FOREIGN KEY (module_code) REFERENCES platform.module_catalog (code)
);

CREATE TABLE platform.tenant_feature_flag (
    tenant_id UUID NOT NULL,
    feature_code VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, feature_code),
    CONSTRAINT fk_tenant_feature_flag_tenant
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_tenant_feature_flag_catalog
        FOREIGN KEY (feature_code) REFERENCES platform.feature_flag_catalog (code)
);

INSERT INTO platform.module_catalog (code, display_name) VALUES
    ('platform', 'Platform Core'),
    ('academy', 'Academy Management'),
    ('commerce', 'Commerce Management');

INSERT INTO platform.permission_catalog (code, module_code, display_name, branch_scoped) VALUES
    ('platform.session.read', 'platform', 'Read current session', FALSE),
    ('platform.memberships.manage', 'platform', 'Manage memberships', FALSE),
    ('platform.roles.manage', 'platform', 'Manage roles and assignments', FALSE),
    ('platform.entitlements.read', 'platform', 'Read tenant entitlements', FALSE),
    ('academy.students.read', 'academy', 'Read students', TRUE),
    ('academy.students.write', 'academy', 'Create and update students', TRUE),
    ('academy.attendance.manage', 'academy', 'Manage attendance', TRUE),
    ('academy.billing.read', 'academy', 'Read academy billing', FALSE),
    ('academy.billing.manage', 'academy', 'Manage academy billing', FALSE),
    ('commerce.customers.read', 'commerce', 'Read commerce customers', FALSE),
    ('commerce.inventory.read', 'commerce', 'Read inventory', TRUE),
    ('commerce.inventory.manage', 'commerce', 'Manage inventory', TRUE),
    ('commerce.cash.read', 'commerce', 'Read cash operations', TRUE),
    ('commerce.cash.manage', 'commerce', 'Manage cash operations', TRUE);

INSERT INTO platform.base_role_template (
    system_key, role_code, display_name, template_version
) VALUES
    ('OWNER', 'owner', 'Owner', 1),
    ('OPERATOR', 'operator', 'Operator', 1),
    ('VIEWER', 'viewer', 'Viewer', 1);

INSERT INTO platform.base_role_template_permission (system_key, permission_code)
SELECT 'OWNER', code FROM platform.permission_catalog;

INSERT INTO platform.base_role_template_permission (system_key, permission_code) VALUES
    ('OPERATOR', 'platform.session.read'),
    ('OPERATOR', 'academy.students.read'),
    ('OPERATOR', 'academy.students.write'),
    ('OPERATOR', 'academy.attendance.manage'),
    ('OPERATOR', 'academy.billing.read'),
    ('OPERATOR', 'commerce.customers.read'),
    ('OPERATOR', 'commerce.inventory.read'),
    ('OPERATOR', 'commerce.inventory.manage'),
    ('OPERATOR', 'commerce.cash.read'),
    ('VIEWER', 'platform.session.read'),
    ('VIEWER', 'academy.students.read'),
    ('VIEWER', 'academy.billing.read'),
    ('VIEWER', 'commerce.customers.read'),
    ('VIEWER', 'commerce.inventory.read'),
    ('VIEWER', 'commerce.cash.read');

INSERT INTO platform.feature_flag_catalog (code, module_code, display_name) VALUES
    ('academy.new-attendance-ui', 'academy', 'New attendance user interface'),
    ('commerce.cycle-counts', 'commerce', 'Inventory cycle counts');
