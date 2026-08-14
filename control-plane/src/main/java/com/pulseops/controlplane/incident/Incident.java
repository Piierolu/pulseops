package com.pulseops.controlplane.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
class Incident {

    @Id
    private UUID id;

    @Column(name = "monitor_id", nullable = false)
    private UUID monitorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(nullable = false, length = 255)
    private String cause;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Incident() {
    }

    static Incident open(UUID monitorId, String cause, Instant now) {
        Incident incident = new Incident();
        incident.id = UUID.randomUUID();
        incident.monitorId = monitorId;
        incident.status = IncidentStatus.OPEN;
        incident.cause = cause;
        incident.openedAt = now;
        incident.updatedAt = now;
        return incident;
    }

    void resolve(Instant now) {
        status = IncidentStatus.RESOLVED;
        resolvedAt = now;
        updatedAt = now;
    }

    IncidentResponse toResponse() {
        return new IncidentResponse(id, monitorId, status, cause, openedAt, resolvedAt, updatedAt);
    }

    UUID getId() {
        return id;
    }
}
