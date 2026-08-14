package com.pulseops.controlplane.incident;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        UUID monitorId,
        IncidentStatus status,
        String cause,
        Instant openedAt,
        Instant resolvedAt,
        Instant updatedAt
) {
}
