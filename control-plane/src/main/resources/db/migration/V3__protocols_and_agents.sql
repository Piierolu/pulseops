ALTER TABLE monitors
    ADD COLUMN monitor_type VARCHAR(20) NOT NULL DEFAULT 'HTTP',
    ADD COLUMN target_host VARCHAR(253),
    ADD COLUMN target_port INTEGER,
    ADD COLUMN dns_record_type VARCHAR(10),
    ADD COLUMN expected_value VARCHAR(2048),
    ADD COLUMN tls_expiry_warning_days INTEGER;

ALTER TABLE monitors ALTER COLUMN target_url DROP NOT NULL;
ALTER TABLE monitors ALTER COLUMN expected_status DROP NOT NULL;

ALTER TABLE monitors ADD CONSTRAINT chk_monitor_type
    CHECK (monitor_type IN ('HTTP', 'TCP', 'DNS', 'TLS'));
ALTER TABLE monitors ADD CONSTRAINT chk_target_port
    CHECK (target_port IS NULL OR target_port BETWEEN 1 AND 65535);
ALTER TABLE monitors ADD CONSTRAINT chk_tls_warning_days
    CHECK (tls_expiry_warning_days IS NULL OR tls_expiry_warning_days BETWEEN 1 AND 365);

ALTER TABLE check_results
    ADD COLUMN details JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE monitoring_agents (
    agent_id VARCHAR(120) PRIMARY KEY,
    location VARCHAR(80) NOT NULL,
    version VARCHAR(40) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_monitoring_agents_last_seen
    ON monitoring_agents (last_seen_at DESC);
