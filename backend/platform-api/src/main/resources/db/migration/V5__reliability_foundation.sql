INSERT INTO platform.permission_catalog (code, module_code, display_name, branch_scoped) VALUES
    ('platform.audit.read', 'platform', 'Read tenant audit events', FALSE),
    ('platform.reliability.read', 'platform', 'Read reliability state', FALSE),
    ('platform.reliability.manage', 'platform', 'Manage reliability recovery', FALSE);

UPDATE platform.base_role_template
   SET template_version = 2
 WHERE system_key = 'OWNER';

INSERT INTO platform.base_role_template_permission (system_key, permission_code) VALUES
    ('OWNER', 'platform.audit.read'),
    ('OWNER', 'platform.reliability.read'),
    ('OWNER', 'platform.reliability.manage');

CREATE TABLE platform.audit_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_identity_id UUID,
    actor_membership_id UUID,
    action_code VARCHAR(120) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(160),
    result VARCHAR(20) NOT NULL,
    failure_code VARCHAR(80),
    correlation_id UUID NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_audit_tenant
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT fk_audit_actor_identity
        FOREIGN KEY (actor_identity_id) REFERENCES platform.identity (id),
    CONSTRAINT fk_audit_actor_membership
        FOREIGN KEY (tenant_id, actor_membership_id)
        REFERENCES platform.membership (tenant_id, id),
    CONSTRAINT ck_audit_result
        CHECK (result IN ('SUCCESS', 'FAILURE', 'REPLAY')),
    CONSTRAINT ck_audit_failure_code
        CHECK (
            (result = 'FAILURE' AND failure_code IS NOT NULL)
            OR (result <> 'FAILURE' AND failure_code IS NULL)
        ),
    CONSTRAINT ck_audit_actor_pair
        CHECK (
            (actor_identity_id IS NULL AND actor_membership_id IS NULL)
            OR (actor_identity_id IS NOT NULL AND actor_membership_id IS NOT NULL)
        )
);

CREATE INDEX idx_audit_tenant_time
    ON platform.audit_event (tenant_id, occurred_at DESC);

CREATE INDEX idx_audit_tenant_action_time
    ON platform.audit_event (tenant_id, action_code, occurred_at DESC);

CREATE OR REPLACE FUNCTION platform.reject_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'platform.audit_event is append-only';
END;
$$;

CREATE TRIGGER trg_audit_event_append_only
BEFORE UPDATE OR DELETE ON platform.audit_event
FOR EACH ROW EXECUTE FUNCTION platform.reject_audit_mutation();

CREATE TABLE platform.idempotency_record (
    tenant_id UUID NOT NULL,
    operation_code VARCHAR(120) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    owner_token UUID NOT NULL,
    locked_until TIMESTAMPTZ NOT NULL,
    response_type VARCHAR(200),
    response_json JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, operation_code, key_hash),
    CONSTRAINT fk_idempotency_tenant
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_idempotency_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_idempotency_hashes
        CHECK (length(key_hash) = 64 AND length(request_hash) = 64),
    CONSTRAINT ck_idempotency_completion
        CHECK (
            (status = 'IN_PROGRESS' AND completed_at IS NULL AND response_json IS NULL)
            OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND response_json IS NOT NULL)
        ),
    CONSTRAINT ck_idempotency_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_idempotency_cleanup
    ON platform.idempotency_record (status, expires_at);

CREATE TABLE platform.outbox_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(160) NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ,
    claim_token UUID,
    claim_expires_at TIMESTAMPTZ,
    last_error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    dispatched_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_outbox_tenant
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_outbox_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_outbox_status
        CHECK (status IN ('PENDING', 'CLAIMED', 'RETRY', 'DISPATCHED', 'DEAD')),
    CONSTRAINT ck_outbox_attempts
        CHECK (attempts >= 0 AND max_attempts > 0 AND attempts <= max_attempts),
    CONSTRAINT ck_outbox_claim
        CHECK (
            (status = 'CLAIMED' AND claimed_at IS NOT NULL AND claim_token IS NOT NULL AND claim_expires_at IS NOT NULL)
            OR (status <> 'CLAIMED')
        ),
    CONSTRAINT ck_outbox_dispatch
        CHECK (
            (status = 'DISPATCHED' AND dispatched_at IS NOT NULL)
            OR (status <> 'DISPATCHED' AND dispatched_at IS NULL)
        )
);

CREATE INDEX idx_outbox_claim
    ON platform.outbox_event (status, available_at, created_at);

CREATE INDEX idx_outbox_tenant_status
    ON platform.outbox_event (tenant_id, status, created_at DESC);

CREATE INDEX idx_outbox_claim_expiry
    ON platform.outbox_event (status, claim_expires_at)
    WHERE status = 'CLAIMED';
