# PulseOps

PulseOps is a portfolio project for distributed synthetic monitoring and automated incident response. It executes HTTP, TCP, DNS, and TLS checks, maintains incident state, exposes end-to-end telemetry, and presents live operational data in a Next.js console.

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/Piierolu/pulseops?quickstart=1)

## Interactive demo

Click **Open in GitHub Codespaces**, create the suggested 4-core codespace, and wait for the terminal to report that the demo is ready. The first build can take several minutes. Open the **PORTS** tab and select port `3000` for PulseOps or port `3001` for Grafana. Forwarded ports remain private to the authenticated Codespaces user.

The environment starts the complete Docker Compose stack and seeds HTTP, TCP, DNS, and TLS monitors. In the PulseOps dashboard:

1. Select a monitor to inspect its configuration and latest results.
2. Pause it and confirm that new checks stop.
3. Edit its target or interval, then resume it.
4. Archive it to retain history without scheduling new checks.
5. Restore it; restored monitors remain paused until explicitly resumed.

Codespaces preserves its Docker volumes across stops. Run `docker compose down -v` only when you want to reset all demo data.

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
- Prometheus operational alerts routed through Alertmanager.
- Bounded Kafka retries and dead-letter topics for commands, results, and heartbeats.
- Clustered persistent Quartz and a transactional Kafka command outbox.
- Database-enforced execution receipts for idempotent TimescaleDB results.
- Incident state machine with automatic opening and recovery.
- Optional Discord webhook notifications.
- Responsive Next.js operations dashboard.
- Agent heartbeats with online/offline detection.
- Docker Compose development environment.

Kubernetes deployment, durable scheduling, generic OIDC authentication, and project RBAC are implemented. Maintenance windows, configurable incident policies, and SLO reporting remain future verticals.

## Requirements

- Docker Desktop with the Linux container engine running.
- Docker Compose v2.

Java, Maven, and Go do not need to be installed locally for the containerized workflow.

## Start the environment

```bash
docker compose up --build -d
docker compose ps
```

Open the operations dashboard at `http://localhost:3000` and Grafana at `http://localhost:3001`. Compose uses a fixed demo identity with `OWNER` access to the legacy team and default project. Dashboard and control-plane host ports bind to loopback so this bypass is not network-accessible. Grafana is provisioned with the `PulseOps Overview` dashboard and Prometheus and Tempo data sources. The dashboard reaches the API through a same-origin server proxy.

Create a monitor for the included Nginx target:

```bash
curl.exe -X POST http://localhost:8082/api/projects/00000000-0000-0000-0000-000000000002/monitors \
  -H "Content-Type: application/json" \
  -d '{"name":"Demo target","targetUrl":"http://demo-target","frequencySeconds":15,"timeoutMs":5000,"expectedStatus":200}'
```

After the first scheduled execution, list monitors and results:

```bash
curl.exe http://localhost:8082/api/projects
curl.exe http://localhost:8082/api/projects/00000000-0000-0000-0000-000000000002/monitors/MONITOR_ID/results
```

Operational endpoints:

```text
GET http://localhost:8082/actuator/health
GET http://localhost:8082/actuator/prometheus
GET http://localhost:9090/-/healthy
GET http://localhost:9093/-/ready
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
GET    /api/me
GET    /api/teams
POST   /api/teams
GET    /api/teams/{teamId}/projects
POST   /api/teams/{teamId}/projects
GET    /api/projects
POST   /api/projects/{projectId}/monitors
GET    /api/projects/{projectId}/monitors
GET    /api/projects/{projectId}/monitors/{monitorId}
PUT    /api/projects/{projectId}/monitors/{monitorId}
POST   /api/projects/{projectId}/monitors/{monitorId}/pause
POST   /api/projects/{projectId}/monitors/{monitorId}/resume
POST   /api/projects/{projectId}/monitors/{monitorId}/restore
DELETE /api/projects/{projectId}/monitors/{monitorId}
GET    /api/projects/{projectId}/monitors/{monitorId}/results?limit=100
GET    /api/projects/{projectId}/incidents?status=OPEN
GET    /api/projects/{projectId}/overview
```

Monitor frequencies range from 10 seconds to 24 hours. HTTP, TCP, DNS, and TLS monitors are supported. Deleting a monitor archives and unschedules it while retaining historical results. The agent blocks private, loopback, link-local, multicast, and unspecified connection targets by default. `ALLOW_PRIVATE_TARGETS=true` is enabled only for the loopback-bound local demo so it can reach `demo-target` inside the Compose network.

## Identity and project access

Production runs the control plane as an OAuth 2.0 resource server. It validates JWT signature, issuer, time claims, and `aud`; users are keyed by immutable issuer and subject. The dashboard uses Authorization Code with PKCE, stores the access token in an encrypted HTTP-only cookie, validates state and nonce, and forwards the bearer token from the same-origin BFF. Its callback is `https://YOUR_PULSEOPS_HOST/api/auth/callback`.

Teams own projects and membership roles apply to every project in the team: `OWNER`, `ADMIN`, `EDITOR`, and `VIEWER`. Viewers read project operations; editors manage monitors; admins create projects; owners additionally establish ownership. Cross-project monitor IDs return `404`.

Every OIDC-mode startup requires an explicit bootstrap identity whose issuer exactly matches the configured OIDC issuer. Set `BOOTSTRAP_ISSUER` and `BOOTSTRAP_SUBJECT` to grant that subject `OWNER` on the migrated legacy team. PulseOps never promotes the first arbitrary login.

Access-token sessions intentionally expire with the provider token instead of persisting refresh tokens in stateless dashboard replicas. A new authorization request normally completes silently while the provider SSO session remains active.

## Repository layout

```text
agent/          Go monitoring agent
contracts/      Kafka message examples and future schemas
control-plane/  Spring Boot API, scheduler, and result consumer
dashboard/      Next.js operations console
docs/adr/       Architecture decision records
observability/  Collector, Tempo, Prometheus, and Grafana configuration
deploy/         Helm chart for application-only Kubernetes deployment
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

Use the restricted operator CLI instead of piping an unbounded DLQ into a producer. Inspection requires an exact coordinate and hides the payload by default:

```bash
docker compose run --rm --no-deps --entrypoint /pulseops-dlq agent inspect \
  --brokers kafka:19092 \
  --topic check.commands.v1.dlq \
  --partition 0 \
  --offset 0
```

Redrive is a dry-run unless `--execute` is present. It requires the SHA-256 printed by inspection, an operator, and a reason. Destinations are hard-coded, payload schemas are validated, recovery headers are removed, trace headers are retained, and audit headers are added.

```bash
docker compose run --rm --no-deps --entrypoint /pulseops-dlq agent redrive \
  --brokers kafka:19092 \
  --topic check.commands.v1.dlq \
  --partition 0 \
  --offset 0 \
  --sha256 PAYLOAD_SHA256 \
  --operator "Your Name" \
  --reason "dependency recovered" \
  --execute
```

The original DLQ record is never committed or deleted by this tool.

## Reliable scheduling

Quartz uses its PostgreSQL job store in clustered mode. Stable jobs and triggers survive restarts, and multiple control-plane replicas coordinate firing through the same database.

A Quartz execution inserts a complete command snapshot into `command_outbox` in a database transaction. A separate cluster-safe publisher claims rows with `FOR UPDATE SKIP LOCKED`, waits for Kafka acknowledgement, and then marks them published. Failed sends use capped exponential backoff. Recovery and uncertain sends retain the same deterministic `executionId`.

Result consumers first insert `executionId` into the ordinary PostgreSQL table `check_execution_receipts`. Only the transaction that claims the receipt may write the TimescaleDB result and advance incident state. This avoids the uniqueness limitation of hypertables and makes Kafka redelivery harmless.

## Operational alerts

Prometheus loads eleven rules covering unavailable services, stopped Quartz, outbox age/backlog, command publication failures, DLQ activity, sustained Kafka retries, and API error rate. Alertmanager is available at `http://localhost:9093`. Its local receiver intentionally has no external destination; production routing belongs in the deployment environment.

## CI and Kubernetes

GitHub Actions run Java, Go, dashboard, Compose, Prometheus, Helm, manifest, image-build, and Trivy checks. Version tags publish SBOM-enabled images to GHCR and sign their digests with keyless Cosign. Production deployment remains an approval-gated manual workflow. The production environment must provide `KUBE_CONFIG` and a complete `PULSEOPS_VALUES` Helm values document as encrypted GitHub secrets.

The Helm chart deploys only the control plane, agent, and dashboard. PostgreSQL, Kafka, the OIDC provider, and the observability backend are external dependencies. It requires a pre-existing secret containing `database-username`, `database-password`, `oidc-client-secret`, and a random `auth-secret` of at least 32 characters:

```bash
helm lint deploy/helm/pulseops --values values-production.yaml
helm upgrade --install pulseops deploy/helm/pulseops \
  --namespace pulseops \
  --create-namespace \
  --values values-production.yaml
```

Helm requires HTTPS when ingress is enabled because the OIDC callback and secure session cookie cannot operate over plaintext. Production values must include issuer, API audience, dashboard client ID/public URL, and the immutable bootstrap issuer/subject. Grafana's external URL is configured at runtime through `config.grafanaUrl`.

## Next milestone

The next vertical will add monitor editing and pause controls, maintenance windows, configurable incident rules and notifications, and SLO reporting.
