# PulseOps

PulseOps is a portfolio project for distributed synthetic monitoring and automated incident response. It executes HTTP, TCP, DNS, and TLS checks, maintains incident state, exposes end-to-end telemetry, and presents live operational data in a Next.js console.

## Current architecture

```text
REST API -> Spring Boot -> Quartz -> Kafka command
                                      |
                                      v
                              Go monitoring agent
                                      |
                                      v
TimescaleDB <- Spring Boot <- Kafka result
                    |
                    v
          Incident engine -> Discord
                    |
                    v
               Next.js dashboard

Java + Go -> OpenTelemetry Collector -> Tempo -> Grafana
Java + Go -> Prometheus ----------------------> Grafana
Kafka failures -> bounded retries -> dead-letter topics
```

Included components:

- Java 21 and Spring Boot control plane.
- Dynamic monitor scheduling with Quartz.
- Kafka command and result topics.
- Go monitoring agent with HTTP, TCP, DNS, and TLS checks.
- TimescaleDB hypertable for historical results.
- Prometheus metrics from Spring Boot and the Go agent.
- OpenTelemetry traces across Quartz, Kafka, checks, HTTP, and PostgreSQL.
- Provisioned Prometheus, Tempo, and Grafana observability stack.
- Bounded Kafka retries and dead-letter topics for commands, results, and heartbeats.
- Incident state machine with automatic opening and recovery.
- Optional Discord webhook notifications.
- Responsive Next.js operations dashboard.
- Agent heartbeats with online/offline detection.
- Docker Compose development environment.

Kubernetes deployment, durable scheduling, and authentication are planned for later verticals.

## Requirements

- Docker Desktop with the Linux container engine running.
- Docker Compose v2.

Java, Maven, and Go do not need to be installed locally for the containerized workflow.

## Start the environment

```bash
docker compose up --build -d
docker compose ps
```

Open the operations dashboard at `http://localhost:3000` and Grafana at `http://localhost:3001`. Grafana is provisioned with the `PulseOps Overview` dashboard and Prometheus and Tempo data sources. The default local administrator is `admin` / `pulseops`; override it in `.env`.

Create a monitor for the included Nginx target:

```bash
curl.exe -X POST http://localhost:8082/api/monitors \
  -H "Content-Type: application/json" \
  -d '{"name":"Demo target","targetUrl":"http://demo-target","frequencySeconds":15,"timeoutMs":5000,"expectedStatus":200}'
```

After the first scheduled execution, list monitors and results:

```bash
curl.exe http://localhost:8082/api/monitors
curl.exe http://localhost:8082/api/monitors/MONITOR_ID/results
```

Operational endpoints:

```text
GET http://localhost:8082/actuator/health
GET http://localhost:8082/actuator/prometheus
GET http://localhost:9090/-/healthy
GET http://localhost:3001/api/health
GET http://localhost:3200/ready
```

Follow the distributed flow:

```bash
docker compose logs -f control-plane agent
```

Stop the environment without deleting data:

```bash
docker compose down
```

Use `docker compose down -v` only when the local database and Kafka data should be removed.

## Tests

Both Dockerfiles expose a test target:

```bash
docker build --target test -t pulseops-control-plane-test ./control-plane
docker build --target test -t pulseops-agent-test ./agent
npm --prefix ./dashboard run typecheck
npm --prefix ./dashboard run build
```

## API implemented

```text
POST   /api/monitors
GET    /api/monitors
GET    /api/monitors/{id}
DELETE /api/monitors/{id}
GET    /api/monitors/{id}/results?limit=100
GET    /api/incidents?status=OPEN
GET    /api/overview
GET    /api/agents
```

Monitor frequencies range from 10 seconds to 24 hours. HTTP, TCP, DNS, and TLS monitors are supported. The agent blocks private, loopback, link-local, multicast, and unspecified connection targets by default. `ALLOW_PRIVATE_TARGETS=true` is enabled only for the local demo agent so it can reach `demo-target` inside the Compose network.

## Repository layout

```text
agent/          Go monitoring agent
contracts/      Kafka message examples and future schemas
control-plane/  Spring Boot API, scheduler, and result consumer
dashboard/      Next.js operations console
docs/adr/       Architecture decision records
observability/  Collector, Tempo, Prometheus, and Grafana configuration
```

## Incident rules

- The first failed check changes a monitor to `DEGRADED`.
- Three consecutive failures open an incident and change it to `DOWN`.
- The first successful recovery check changes it to `RECOVERING`.
- Two consecutive successful checks resolve the incident and return it to `UP`.

Set `DISCORD_WEBHOOK_URL` in `.env` to enable incident notifications.

## Observability

The control plane uses the OpenTelemetry Java agent, while the Go agent creates explicit Kafka and check spans and instruments outbound HTTP checks. W3C `traceparent` and `tracestate` headers connect Quartz dispatch, command consumption, protocol execution, result publication, and result persistence in one trace.

Prometheus scrapes:

```text
control-plane:8080/actuator/prometheus
agent:9464/metrics
```

Metric labels are intentionally limited to protocol, status, outcome, and topic. Monitor IDs, execution IDs, URLs, and error text are trace attributes or logs rather than Prometheus labels.

Set `OTEL_TRACE_SAMPLE_RATIO` between `0.0` and `1.0` to control trace volume. The local default is `1.0`.

## Kafka recovery

Each consumer processes one record to a terminal state before committing its offset:

- Successful or location-skipped commands are committed.
- Transient command processing failures retry in place with exponential backoff.
- Malformed commands go directly to `check.commands.v1.dlq`.
- Exhausted commands go to `check.commands.v1.dlq` before their source offset is committed.
- Result and heartbeat listener failures retry in Spring Kafka before recovery to their respective DLQ.
- A failed DLQ publication is fatal and leaves the source record uncommitted for redelivery after restart.

The local defaults are three total attempts, 500 ms initial backoff, and a 5 second maximum backoff. Configure them through the `KAFKA_RETRY_*` variables in `.env`.

List recovery topics:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:19092 --list
```

Inspect command dead letters with headers:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:19092 \
  --topic check.commands.v1.dlq \
  --from-beginning \
  --property print.headers=true
```

Dead letters retain the original payload and carry source topic, partition, offset, error, and trace headers. Review and correct a payload before manually publishing it back to its source topic; automatic redrive is intentionally not enabled to prevent poison-message loops.

## Next milestone

The next vertical will focus on authentication, persistent Quartz scheduling or an outbox, alert rules for Prometheus, and a Kubernetes deployment.
