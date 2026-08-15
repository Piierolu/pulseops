# PulseOps Helm chart

This chart deploys the PulseOps control plane, monitoring agents, and dashboard. It does not install PostgreSQL, Kafka, Prometheus, Tempo, Grafana, an OIDC provider, or an ingress controller.

Create a secret before installation:

```bash
kubectl create namespace pulseops
kubectl -n pulseops create secret generic pulseops-secrets \
  --from-literal=database-username=pulseops \
  --from-literal=database-password=CHANGE_ME \
  --from-literal=oidc-client-secret=CHANGE_ME \
  --from-literal=auth-secret=CHANGE_ME_WITH_AT_LEAST_32_RANDOM_CHARACTERS
```

Render and validate:

```bash
helm lint . --values values-production.yaml
helm template pulseops . --values values-production.yaml
```

Production values must set immutable image tags, external database and Kafka endpoints, an existing secret, ingress DNS/TLS settings, resource budgets, the external Grafana URL, the OTLP endpoint, and generic OIDC settings. The provider must register `https://YOUR_HOST/api/auth/callback` as a callback and issue access tokens containing `security.audience`.

`security.bootstrapIssuer` must equal `security.issuerUri`; `security.bootstrapSubject` is the immutable OIDC subject granted `OWNER` on the legacy team. Both values are mandatory to avoid locking migrated monitors behind an unowned project. Ingress requires a TLS secret.

The manual GitHub deployment workflow expects encrypted `KUBE_CONFIG` and `PULSEOPS_VALUES` secrets. `PULSEOPS_VALUES` must be a complete environment-specific values document, including `existingSecret`, external endpoints, and every `security` value. The chart schema rejects placeholder endpoints and missing bootstrap identity.
