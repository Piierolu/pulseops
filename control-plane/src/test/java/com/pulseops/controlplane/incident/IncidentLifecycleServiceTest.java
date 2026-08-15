package com.pulseops.controlplane.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class IncidentLifecycleServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ApplicationEventPublisher events;

    @Test
    void publishesRecoveryAndCountsEachResolvedIncident() {
        UUID monitorId = UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a");
        UUID incidentId = UUID.fromString("48a53e1c-796a-4799-909e-a6db26d9bd90");
        Instant now = Instant.parse("2026-08-14T12:00:00Z");
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        when(jdbcTemplate.queryForList(
                argThat(sql -> sql.contains("UPDATE incidents")),
                eq(UUID.class),
                eq(Timestamp.from(now)), eq(Timestamp.from(now)), eq(monitorId)
        )).thenReturn(List.of(incidentId));
        IncidentLifecycleService service = new IncidentLifecycleService(jdbcTemplate, events, meters);

        service.resolveOpen(monitorId, "Public API", now);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        org.mockito.Mockito.verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(IncidentChangedEvent.class);
        IncidentChangedEvent changed = (IncidentChangedEvent) eventCaptor.getValue();
        assertThat(changed.incidentId()).isEqualTo(incidentId);
        assertThat(changed.monitorId()).isEqualTo(monitorId);
        assertThat(changed.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(meters.counter("pulseops.incidents.resolved").count()).isEqualTo(1.0);
    }
}
