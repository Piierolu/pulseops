ALTER TABLE monitors
    ADD COLUMN lifecycle_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE command_outbox
    ADD COLUMN monitor_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN cancelled_at TIMESTAMPTZ;

DROP INDEX idx_command_outbox_ready;

CREATE INDEX idx_command_outbox_ready
    ON command_outbox (available_at, created_at)
    WHERE published_at IS NULL AND cancelled_at IS NULL;

CREATE INDEX idx_command_outbox_execution_lifecycle
    ON command_outbox (execution_id, monitor_id, monitor_version);
