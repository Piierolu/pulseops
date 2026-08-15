package com.pulseops.controlplane.incident;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pulseops.controlplane.execution.CheckResultMessage;
import com.pulseops.controlplane.execution.CommandOutboxRepository;
import com.pulseops.controlplane.monitor.MonitorResponse;
import com.pulseops.controlplane.monitor.MonitorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class IncidentEvaluatorTest {

    private static final UUID MONITOR_ID = UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a");
    private static final UUID EXECUTION_ID = UUID.fromString("48a53e1c-796a-4799-909e-a6db26d9bd90");

    @Mock
    private MonitorStateRepository states;

    @Mock
    private IncidentRepository incidents;

    @Mock
    private MonitorService monitors;

    @Mock
    private CommandOutboxRepository outbox;

    @Mock
    private ApplicationEventPublisher events;

    private IncidentEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new IncidentEvaluator(
                states, incidents, monitors, outbox, events,
                Clock.fixed(Instant.parse("2026-08-14T12:00:00Z"), ZoneOffset.UTC),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void ignoresResultsWhileTheMonitorIsPaused() {
        MonitorResponse monitor = mock(MonitorResponse.class);
        when(monitors.findForBackgroundForUpdate(MONITOR_ID)).thenReturn(monitor);
        when(monitor.enabled()).thenReturn(false);

        evaluator.evaluate(result());

        verifyNoInteractions(outbox, states, incidents);
    }

    @Test
    void ignoresResultsFromAnEarlierLifecycle() {
        MonitorResponse monitor = mock(MonitorResponse.class);
        when(monitors.findForBackgroundForUpdate(MONITOR_ID)).thenReturn(monitor);
        when(monitor.enabled()).thenReturn(true);
        when(monitor.lifecycleVersion()).thenReturn(4L);
        when(outbox.belongsToLifecycle(EXECUTION_ID, MONITOR_ID, 4L)).thenReturn(false);

        evaluator.evaluate(result());

        verifyNoInteractions(states, incidents);
    }

    private static CheckResultMessage result() {
        return new CheckResultMessage(
                EXECUTION_ID, MONITOR_ID, "agent-1", "local", "FAILURE", 100,
                null, "timeout", Map.of(), Instant.parse("2026-08-14T11:59:59Z")
        );
    }
}
