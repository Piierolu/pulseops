# ADR 0002: Protocol checks and agent heartbeats

- Status: Accepted
- Date: 2026-08-14

## Context

The first vertical only supported HTTP checks and had no authoritative way to determine whether an agent was still connected. PulseOps needs additional network protocols without splitting the agent into separate executables or coupling protocol-specific fields to Kafka message versions.

## Decision

Use one versioned check command with a protocol discriminator and optional configuration fields for HTTP, TCP, DNS, and TLS. A Go runner selects the protocol implementation. HTTP, TCP, and TLS share the same outbound network policy and reject restricted addresses unless the agent explicitly enables private targets.

Agents publish a heartbeat every 15 seconds to `agent.heartbeats.v1`. The control plane stores the latest heartbeat in PostgreSQL and considers an agent offline after 45 seconds without an update.

Protocol-specific result metadata is stored as JSONB in the TimescaleDB result hypertable.

## Consequences

- One agent binary can execute all supported checks.
- Existing HTTP monitor data remains valid through an incremental migration.
- The command contract contains nullable fields; future incompatible changes still require a new topic version.
- Heartbeat status is eventually consistent and does not require a direct agent-to-API connection.
- Private targets remain disabled by default outside the local Compose demonstration.
