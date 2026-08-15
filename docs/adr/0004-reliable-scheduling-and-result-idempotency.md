# ADR 0004: Reliable scheduling and result idempotency

- Status: Accepted
- Date: 2026-08-14

## Context

In-memory Quartz reconstructed schedules on startup and could not coordinate multiple control-plane replicas. Waiting for a Kafka producer acknowledgement exposed failures but did not make the transition from a database-backed schedule to Kafka durable. Result idempotency used a read-before-write query that could race, while TimescaleDB cannot enforce a global unique execution ID on a hypertable partitioned by time.

## Decision

Quartz uses the PostgreSQL JDBC job store with clustering, stable job keys, recovery requests, and an idempotent startup reconciler. Existing `http-monitors` job groups remain unchanged so persisted deployments do not create parallel schedules during migration.

Quartz jobs no longer publish directly to Kafka. They derive a deterministic execution ID from monitor, location, and scheduled fire time, then insert a complete command snapshot into `command_outbox`. The unique schedule-slot constraint makes crash recovery idempotent.

Scheduled outbox publishers claim bounded batches with PostgreSQL `FOR UPDATE SKIP LOCKED` and leases. Kafka publication occurs outside the claim transaction. Broker acknowledgement marks a row published; failure clears the lease and applies capped exponential backoff. A crash after broker acknowledgement can produce a duplicate, but it retains the same execution ID.

W3C trace context is stored with the outbox row and restored before Kafka publication, preserving the scheduling-to-agent distributed trace across asynchronous polling.

Each result transaction first inserts its execution ID into the ordinary `check_execution_receipts` table with `ON CONFLICT DO NOTHING`. Only the successful claimant writes the TimescaleDB row and evaluates incidents. The receipt rolls back if either operation fails.

The `CheckResult` JPA identity now matches the hypertable primary key `(id, checked_at)`.

## Consequences

- Schedules survive restarts and multiple control-plane replicas can safely share work.
- Command delivery is durable and at-least-once across PostgreSQL and Kafka without distributed transactions.
- Kafka redelivery cannot duplicate a result or incident transition while its receipt is retained.
- Outbox and receipt tables grow over time and require a future retention policy tied to Kafka and DLQ retention.
- Network checks can still execute more than once after an uncertain publication. Preventing duplicate external execution would require a durable agent inbox.
- A failed Quartz job refires at most twice immediately; unbounded refire loops are prohibited.
