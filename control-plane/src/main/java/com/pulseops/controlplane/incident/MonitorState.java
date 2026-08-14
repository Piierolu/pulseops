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
@Table(name = "monitor_states")
class MonitorState {

    @Id
    @Column(name = "monitor_id")
    private UUID monitorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MonitorStatus status;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "consecutive_successes", nullable = false)
    private int consecutiveSuccesses;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MonitorState() {
    }

    static MonitorState pending(UUID monitorId, Instant now) {
        MonitorState state = new MonitorState();
        state.monitorId = monitorId;
        state.status = MonitorStatus.PENDING;
        state.updatedAt = now;
        return state;
    }

    IncidentStateMachine.CurrentState current() {
        return new IncidentStateMachine.CurrentState(status, consecutiveFailures, consecutiveSuccesses);
    }

    void apply(IncidentStateMachine.Outcome outcome, Instant now) {
        status = outcome.status();
        consecutiveFailures = outcome.consecutiveFailures();
        consecutiveSuccesses = outcome.consecutiveSuccesses();
        updatedAt = now;
    }
}
