package com.pulseops.controlplane.incident;

import com.pulseops.controlplane.execution.CheckResultMessage;
import com.pulseops.controlplane.monitor.MonitorResponse;
import com.pulseops.controlplane.monitor.MonitorService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class IncidentEvaluator {

    private static final String FAILURE_CAUSE = "Three consecutive monitoring checks failed";

    private final MonitorStateRepository states;
    private final IncidentRepository incidents;
    private final MonitorService monitors;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Counter openedCounter;
    private final Counter resolvedCounter;

    IncidentEvaluator(
            MonitorStateRepository states,
            IncidentRepository incidents,
            MonitorService monitors,
            ApplicationEventPublisher events,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.states = states;
        this.incidents = incidents;
        this.monitors = monitors;
        this.events = events;
        this.clock = clock;
        this.openedCounter = Counter.builder("pulseops.incidents.opened").register(meterRegistry);
        this.resolvedCounter = Counter.builder("pulseops.incidents.resolved").register(meterRegistry);
    }

    public void evaluate(CheckResultMessage result) {
        if (monitors.findForBackgroundForUpdate(result.monitorId()).archivedAt() != null) {
            return;
        }
        Instant now = clock.instant();
        MonitorState state = states.findForUpdate(result.monitorId())
                .orElseGet(() -> MonitorState.pending(result.monitorId(), now));
        boolean successful = "SUCCESS".equalsIgnoreCase(result.status());
        IncidentStateMachine.Outcome outcome = IncidentStateMachine.evaluate(state.current(), successful);
        state.apply(outcome, now);
        states.save(state);

        if (outcome.openIncident()) {
            openIncident(result.monitorId(), now);
        } else if (outcome.resolveIncident()) {
            resolveIncident(result.monitorId(), now);
        }
    }

    private void openIncident(java.util.UUID monitorId, Instant now) {
        if (incidents.findFirstByMonitorIdAndStatus(monitorId, IncidentStatus.OPEN).isPresent()) {
            return;
        }
        Incident incident = incidents.save(Incident.open(monitorId, FAILURE_CAUSE, now));
        MonitorResponse monitor = monitors.findForBackground(monitorId);
        events.publishEvent(new IncidentChangedEvent(
                incident.getId(), monitorId, monitor.name(), IncidentStatus.OPEN, now
        ));
        openedCounter.increment();
    }

    private void resolveIncident(java.util.UUID monitorId, Instant now) {
        incidents.findFirstByMonitorIdAndStatus(monitorId, IncidentStatus.OPEN).ifPresent(incident -> {
            incident.resolve(now);
            MonitorResponse monitor = monitors.findForBackground(monitorId);
            events.publishEvent(new IncidentChangedEvent(
                    incident.getId(), monitorId, monitor.name(), IncidentStatus.RESOLVED, now
            ));
            resolvedCounter.increment();
        });
    }
}
