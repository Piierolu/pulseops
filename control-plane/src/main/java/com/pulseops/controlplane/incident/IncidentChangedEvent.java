package com.pulseops.controlplane.incident;

import java.time.Instant;
import java.util.UUID;

record IncidentChangedEvent(
        UUID incidentId,
        UUID monitorId,
        String monitorName,
        IncidentStatus status,
        Instant occurredAt
) {
}
