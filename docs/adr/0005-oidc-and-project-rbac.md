# ADR 0005: OIDC identity and project RBAC

- Status: Accepted
- Date: 2026-08-15

## Context

PulseOps originally exposed one global monitor namespace and trusted an unauthenticated dashboard proxy. That model could not isolate teams, authorize monitor changes, or safely expose the application outside a local machine. Quartz and Kafka workers cannot depend on an interactive user context, so authorization must protect user-facing access without changing background execution contracts.

## Decision

The control plane is an OAuth 2.0 resource server in production. It accepts access tokens from one configurable OIDC issuer and validates signature, issuer, time claims, and a required API audience. Application users are keyed by immutable issuer and subject; email and display name are metadata only.

The dashboard is a same-origin BFF using Authorization Code with PKCE. It validates state and nonce and stores the access token in an encrypted, HTTP-only, SameSite cookie. Mutations require the configured dashboard origin. Sessions expire with the access token; stateless dashboard replicas do not persist or rotate refresh tokens.

Teams contain projects and users receive one inherited team role: `OWNER`, `ADMIN`, `EDITOR`, or `VIEWER`. Monitors belong to exactly one project. User-facing monitor, result, incident, and overview queries are scoped by project at the database query boundary. Cross-project resource lookups return `404`.

Quartz jobs, outbox publication, Kafka result processing, and agent heartbeats remain machine flows keyed by monitor ID. They use explicit global monitor lookup methods and do not depend on an end-user security context. Agent inventory remains platform infrastructure and is not exposed through tenant APIs.

Compose may use a fixed demo principal, but its authenticated HTTP entry points bind to loopback. Production never falls back to demo mode and requires an explicit bootstrap issuer and subject for ownership of migrated monitors.

Monitor deletion is soft archival. It unschedules the Quartz job, resolves any open incident, suppresses late incident transitions, and preserves project ownership for historical result access.

## Consequences

- One issuer and audience are configured per PulseOps deployment.
- Provider-specific token role claims do not control PulseOps authorization.
- Team roles apply to all projects in that team; project-specific memberships would require a later schema extension.
- Access-token expiry can trigger a new OIDC authorization redirect, normally satisfied by the provider SSO session.
- Kafka, PostgreSQL, metrics, traces, and agent channels still require infrastructure-level network policy and credentials.
- A production deployment must register the dashboard callback, provide client/session secrets, and identify the initial owner before rollout.
