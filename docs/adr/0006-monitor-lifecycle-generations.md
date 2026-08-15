# ADR 0006: Monitor lifecycle generations

- Status: Accepted
- Date: 2026-08-15

## Context

Pausing, editing, archiving, and restoring a monitor can overlap with Quartz dispatch, outbox publication, and delayed Kafka results. Checking only the current `enabled` or `archived_at` value is insufficient: a result created before a restore or resume could otherwise change the new lifecycle state and open an obsolete incident.

## Decision

Each monitor has a monotonically increasing lifecycle generation. Configuration replacement, pause, resume, archive, and restore advance that generation while holding the monitor row lock. Dispatch takes the same lock and stores the observed generation with its outbox record.

Lifecycle changes cancel unpublished outbox rows, reset the monitor state to `PENDING`, and resolve open incidents. Results remain part of immutable check history, but incident evaluation requires the monitor to be active and the result execution to belong to the current generation. Missing or stale outbox provenance suppresses state transitions.

The relational monitor state is canonical. Quartz changes run after the database commit, and a periodic reconciler repairs missing jobs, removes jobs for inactive monitors, and corrects stale trigger intervals. Restoring an archived monitor produces a paused monitor so resumption is always explicit.

## Consequences

- Delayed checks can still consume agent capacity, but cannot corrupt the current lifecycle state.
- Historical results include checks from previous generations by design.
- Any manual result without matching outbox provenance is stored but does not affect incidents.
- Every configuration replacement starts a fresh incident evaluation baseline.
- Quartz can briefly lag a committed lifecycle change, but dispatch rechecks the locked database state.
- Control-plane releases use a coordinated `Recreate` deployment because pre-generation replicas cannot safely process lifecycle-aware outbox rows.
