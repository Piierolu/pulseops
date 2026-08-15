CREATE TABLE command_outbox (
  execution_id UUID PRIMARY KEY,
  monitor_id UUID NOT NULL REFERENCES monitors(id) ON DELETE CASCADE,
  location VARCHAR(80) NOT NULL,
  scheduled_at TIMESTAMPTZ NOT NULL,
  destination_topic VARCHAR(249) NOT NULL,
  message_key VARCHAR(120) NOT NULL,
  payload JSONB NOT NULL,
  traceparent VARCHAR(55),
  tracestate VARCHAR(512),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  claim_token UUID,
  claimed_until TIMESTAMPTZ,
  published_at TIMESTAMPTZ,
  last_error VARCHAR(2048),
  UNIQUE (monitor_id, location, scheduled_at)
);

CREATE INDEX idx_command_outbox_ready
  ON command_outbox (available_at, created_at)
  WHERE published_at IS NULL;

CREATE TABLE check_execution_receipts (
  execution_id UUID PRIMARY KEY,
  monitor_id UUID NOT NULL,
  checked_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_check_execution_receipts_received_at
  ON check_execution_receipts (received_at);
