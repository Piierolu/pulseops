CREATE TABLE monitor_states (
    monitor_id UUID PRIMARY KEY REFERENCES monitors(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'UP', 'DEGRADED', 'DOWN', 'RECOVERING')),
    consecutive_failures INTEGER NOT NULL DEFAULT 0 CHECK (consecutive_failures >= 0),
    consecutive_successes INTEGER NOT NULL DEFAULT 0 CHECK (consecutive_successes >= 0),
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO monitor_states (monitor_id, status, consecutive_failures, consecutive_successes, updated_at)
SELECT id, 'PENDING', 0, 0, created_at
FROM monitors;

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    monitor_id UUID NOT NULL REFERENCES monitors(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
    cause VARCHAR(255) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_incidents_one_open_per_monitor
    ON incidents (monitor_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_incidents_status_opened_at
    ON incidents (status, opened_at DESC);
