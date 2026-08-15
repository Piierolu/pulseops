# PulseOps Helm chart

This chart deploys the PulseOps control plane, monitoring agents, and dashboard. It does not install PostgreSQL, Kafka, Prometheus, Tempo, Grafana, an OIDC provider, or an ingress controller.

Create a secret before installation:

```bash
kubectl create namespace pulseops
kubectl -n pulseops create secret generic pulseops-secrets \
  --from-literal=database-username=pulseops \
  --from-literal=database-password=CHANGE_ME
```

Render and validate:

```bash
helm lint . --values values-production.yaml
helm template pulseops . --values values-production.yaml
```

Production values must set immutable image tags, external database and Kafka endpoints, an existing secret, ingress DNS/TLS settings, resource budgets, the external Grafana URL, and the OTLP endpoint. The chart never creates secrets from values.

Dashboard mutations default to disabled. Until PulseOps application authentication is enabled, ingress must set `ingress.externalAuthentication=true` and carry external-auth annotations, or explicitly set `ingress.allowPublicReadOnly=true`; the latter exposes monitoring status publicly but cannot be combined with unauthenticated mutations.

The manual GitHub deployment workflow expects encrypted `KUBE_CONFIG` and `PULSEOPS_VALUES` secrets. `PULSEOPS_VALUES` must be a complete environment-specific values document, including `existingSecret`, `config.databaseUrl`, `config.kafkaBrokers`, `config.otlpEndpoint`, and `config.grafanaUrl`.
