ALTER TABLE platform.audit_event
    DROP CONSTRAINT fk_audit_actor_membership;

ALTER TABLE platform.audit_event
    ADD CONSTRAINT fk_audit_actor_membership
    FOREIGN KEY (tenant_id, actor_membership_id, actor_identity_id)
    REFERENCES platform.membership (tenant_id, id, identity_id);
