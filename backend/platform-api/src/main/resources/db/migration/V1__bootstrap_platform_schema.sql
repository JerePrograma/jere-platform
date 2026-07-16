CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE platform.bootstrap_marker (
    id SMALLINT PRIMARY KEY,
    initialized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_bootstrap_marker_singleton CHECK (id = 1)
);

INSERT INTO platform.bootstrap_marker (id)
VALUES (1)
ON CONFLICT (id) DO NOTHING;
