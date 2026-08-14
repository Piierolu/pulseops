# ADR 0001: Initial architecture

- Status: Accepted
- Date: 2026-08-14

## Context

PulseOps needs to demonstrate distributed check execution without introducing a large set of independently deployed business services before the first useful workflow exists.

## Decision

Use a modular Spring Boot control plane, Go monitoring agents, Kafka for commands and results, Quartz for dynamic schedules, and PostgreSQL with TimescaleDB for configuration and historical results.

The first vertical supports HTTP checks in one logical location. Commands and results are versioned through topic names. Agents use manual Kafka commits and publish a result before acknowledging its command, providing at-least-once delivery. The control plane treats `executionId` as the idempotency key.

Quartz uses an in-memory job store during this stage. Monitor definitions remain in PostgreSQL and schedules are reconciled when the application starts.

## Consequences

- One deployable control plane is easier to understand, test, and operate than premature microservices.
- Kafka still exercises the distributed boundary between the scheduler and agents.
- Restarting the control plane rebuilds Quartz schedules from monitor definitions.
- Running multiple control-plane replicas requires moving Quartz to its JDBC job store or electing one scheduler leader.
- More locations can be added by publishing one command per location and assigning agents a location-specific consumer group.
