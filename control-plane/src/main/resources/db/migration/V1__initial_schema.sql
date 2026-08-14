CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE monitors (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    target_url VARCHAR(2048) NOT NULL,
    frequency_seconds INTEGER NOT NULL CHECK (frequency_seconds BETWEEN 10 AND 86400),
    timeout_ms INTEGER NOT NULL CHECK (timeout_ms BETWEEN 100 AND 60000),
    expected_status INTEGER NOT NULL CHECK (expected_status BETWEEN 100 AND 599),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE check_results (
    id UUID NOT NULL,
    execution_id UUID NOT NULL,
    monitor_id UUID NOT NULL,
    agent_id VARCHAR(120) NOT NULL,
    location VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    latency_ms BIGINT NOT NULL CHECK (latency_ms >= 0),
    status_code INTEGER,
    error VARCHAR(2048),
    checked_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id, checked_at)
);

SELECT create_hypertable('check_results', by_range('checked_at'), if_not_exists => TRUE);

CREATE INDEX idx_check_results_monitor_checked_at
    ON check_results (monitor_id, checked_at DESC);
CREATE INDEX idx_check_results_execution
    ON check_results (execution_id);
