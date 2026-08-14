# ADR 0003: Observability and Kafka recovery

- Status: Accepted
- Date: 2026-08-14

## Context

PulseOps dispatches scheduled checks through Kafka and persists asynchronous results. Logs and database-derived dashboard aggregates were sufficient for the first verticals, but they could not explain latency across Quartz, Kafka, protocol execution, and persistence. Consumer failures also had implicit framework behavior. In particular, the Go reader could fetch a later command after a failed publication and then commit an offset beyond the failed record.

The local platform needs enough observability and recovery behavior to diagnose failures without introducing a production-scale service mesh or a second message workflow.

## Decision

### Traces

The Spring Boot control plane uses the pinned OpenTelemetry Java agent for automatic MVC, Quartz, Kafka, JDBC, and HTTP client instrumentation. The Go agent uses the OpenTelemetry SDK with explicit consumer, producer, and protocol-neutral check spans. Outbound HTTP checks use `otelhttp`.

Both applications export OTLP over HTTP to an OpenTelemetry Collector. The collector batches and forwards traces to Tempo. W3C Trace Context is propagated in Kafka headers, including dead-letter messages.

The default local sampling ratio is 100 percent for easy inspection and is configurable through `OTEL_TRACE_SAMPLE_RATIO`.

### Metrics

Prometheus scrapes Spring Boot Actuator and the Go agent directly. Grafana is provisioned with Prometheus and Tempo data sources and a PulseOps overview dashboard.

Custom metric labels are bounded to protocol, status, outcome, and topic. High-cardinality identifiers and free-form values are excluded from labels.

### Kafka recovery

The control plane consumes results and heartbeats as raw strings and performs JSON conversion inside each listener. This ensures malformed JSON enters the same explicit error policy as service and database failures. Spring Kafka performs bounded exponential retries and publishes exhausted records to topic-specific DLQs.

The Go agent does not fetch another command until the current record is terminal. It retries transient processing and result-publication failures in place. Malformed commands and exhausted failures are published to `check.commands.v1.dlq`. The source offset is committed only after successful result or DLQ publication. Commit or DLQ failures stop the worker so Kafka can redeliver the record after restart.

Command keys use `executionId` rather than location. Location-specific consumer groups still receive and filter commands, while agents in the same location can use all topic partitions.

The command publisher waits for broker acknowledgement with a bounded timeout so Quartz can observe publication failures.

## Consequences

- One trace can connect scheduling, Kafka delivery, execution, result handling, and database work across Java and Go.
- Grafana and Prometheus add local resource usage but remain optional deployment components outside Compose.
- Blocking retries preserve partition order but pause that partition during backoff.
- DLQ messages are durable in the Kafka volume and visible through metrics, but redrive remains a reviewed manual operation.
- The source database transaction and Kafka offset are not atomic. Result idempotency reduces duplicate effects but a transactional outbox or inbox remains future work.
- In-memory Quartz still cannot guarantee command recovery if the process stops after a trigger fires. Persistent Quartz or an outbox is a separate decision.
- Tempo uses local ephemeral trace storage with 24-hour retention. This is appropriate for local development, not production.

## Rejected alternatives

- In-process Java OpenTelemetry dependencies were rejected to avoid overlapping instrumentation and code-level changes already covered by the Java agent.
- Non-blocking retry topics were deferred because they add topic and ordering complexity without improving the current single-agent local deployment.
- Automatic DLQ redrive was rejected because malformed payloads could create an unbounded poison-message loop.
- High-cardinality labels were rejected because monitor and execution growth would make Prometheus memory use unpredictable.
