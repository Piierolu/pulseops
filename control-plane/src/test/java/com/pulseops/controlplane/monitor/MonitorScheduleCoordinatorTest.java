package com.pulseops.controlplane.monitor;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitorScheduleCoordinatorTest {

    @Mock
    private MonitorRepository monitors;

    @Mock
    private MonitorScheduleService schedules;

    @InjectMocks
    private MonitorScheduleCoordinator coordinator;

    @Test
    void reconcilesFromTheLockedCanonicalMonitor() {
        UUID monitorId = UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a");
        Monitor monitor = new Monitor(
                monitorId,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "Public API",
                MonitorType.HTTP,
                "https://example.com/health",
                null, null, null, null, null,
                60, 5000, 200, true, Instant.parse("2026-08-14T12:00:00Z")
        );
        when(monitors.findByIdForUpdate(monitorId)).thenReturn(Optional.of(monitor));

        coordinator.reconcile(monitorId);

        verify(schedules).reconcile(monitor);
    }
}
