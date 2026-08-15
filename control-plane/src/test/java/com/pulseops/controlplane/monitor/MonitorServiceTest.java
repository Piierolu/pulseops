package com.pulseops.controlplane.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pulseops.controlplane.incident.IncidentLifecycleService;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private MonitorRepository repository;

    @Mock
    private MonitorScheduleCoordinator schedules;

    @Mock
    private IncidentLifecycleService incidents;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MonitorService service;

    @BeforeEach
    void setUp() {
        service = new MonitorService(
                repository, schedules, incidents, jdbcTemplate, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsAndSchedulesAnHttpMonitor() {
        when(repository.save(org.mockito.ArgumentMatchers.any(Monitor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateMonitorRequest(
                "Public API", URI.create("https://example.com/health"), 60, 5000, 200
        );

        MonitorResponse response = service.create(PROJECT_ID, request);

        assertThat(response.name()).isEqualTo("Public API");
        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.targetUrl()).isEqualTo("https://example.com/health");
        assertThat(response.createdAt()).isEqualTo(NOW);
        verify(schedules).reconcile(response.id());
    }

    @Test
    void doesNotFailCreationWhenImmediateScheduleReconciliationFails() {
        when(repository.save(org.mockito.ArgumentMatchers.any(Monitor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("Quartz unavailable"))
                .when(schedules).reconcile(org.mockito.ArgumentMatchers.any(UUID.class));

        assertThatCode(() -> service.create(PROJECT_ID, new CreateMonitorRequest(
                "Public API", URI.create("https://example.com/health"), 60, 5000, 200
        ))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedSchemes() {
        var request = new CreateMonitorRequest(
                "File", URI.create("file:///etc/passwd"), 60, 5000, 200
        );

        assertThatThrownBy(() -> service.create(PROJECT_ID, request))
                .isInstanceOf(InvalidTargetException.class)
                .hasMessage("Only HTTP and HTTPS targets are supported");
    }

    @Test
    void rejectsTimeoutLongerThanFrequency() {
        var request = new CreateMonitorRequest(
                "Slow API", URI.create("https://example.com"), 10, 10000, 200
        );

        assertThatThrownBy(() -> service.create(PROJECT_ID, request))
                .isInstanceOf(InvalidTargetException.class)
                .hasMessage("Timeout must be shorter than the monitor frequency");
    }

    @Test
    void createsATcpMonitor() {
        when(repository.save(org.mockito.ArgumentMatchers.any(Monitor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateMonitorRequest(
                "PostgreSQL",
                MonitorType.TCP,
                null,
                "db.internal",
                5432,
                null,
                null,
                null,
                30,
                3000,
                null
        );

        MonitorResponse response = service.create(PROJECT_ID, request);

        assertThat(response.type()).isEqualTo(MonitorType.TCP);
        assertThat(response.host()).isEqualTo("db.internal");
        assertThat(response.port()).isEqualTo(5432);
    }

    @Test
    void appliesTlsDefaults() {
        when(repository.save(org.mockito.ArgumentMatchers.any(Monitor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateMonitorRequest(
                "Certificate",
                MonitorType.TLS,
                null,
                "example.com",
                null,
                null,
                null,
                null,
                60,
                5000,
                null
        );

        MonitorResponse response = service.create(PROJECT_ID, request);

        assertThat(response.port()).isEqualTo(443);
        assertThat(response.tlsExpiryWarningDays()).isEqualTo(30);
    }

    @Test
    void rejectsUnsupportedDnsRecordTypes() {
        var request = new CreateMonitorRequest(
                "Mail",
                MonitorType.DNS,
                null,
                "example.com",
                null,
                "MX",
                null,
                null,
                60,
                5000,
                null
        );

        assertThatThrownBy(() -> service.create(PROJECT_ID, request))
                .isInstanceOf(InvalidTargetException.class)
                .hasMessage("DNS record type must be A, AAAA, CNAME, or TXT");
    }

    @Test
    void doesNotResolveAMonitorThroughAnotherProject() {
        UUID monitorId = UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a");
        UUID otherProject = UUID.fromString("48a53e1c-796a-4799-909e-a6db26d9bd90");
        when(repository.findByIdAndProjectId(monitorId, otherProject))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(otherProject, monitorId))
                .isInstanceOf(MonitorNotFoundException.class);
    }

    @Test
    void archivesAndUnschedulesWithoutDeletingTheMonitor() {
        UUID monitorId = UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a");
        Monitor monitor = monitor(monitorId);
        AtomicReference<Monitor> persisted = new AtomicReference<>(monitor);
        when(repository.save(org.mockito.ArgumentMatchers.any(Monitor.class))).thenAnswer(invocation -> {
            Monitor saved = invocation.getArgument(0);
            persisted.set(saved);
            return saved;
        });
        when(repository.findActiveByIdAndProjectIdForUpdate(monitorId, PROJECT_ID))
                .thenReturn(Optional.of(monitor));

        service.archive(PROJECT_ID, monitorId);

        assertThat(persisted.get().isEnabled()).isFalse();
        assertThat(persisted.get().getArchivedAt()).isEqualTo(NOW);
        verify(schedules).reconcile(monitorId);
        verify(incidents).resolveOpen(monitorId, "Public API", NOW);
    }

    @Test
    void pausesAndInvalidatesTheCurrentLifecycle() {
        UUID monitorId = UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a");
        Monitor monitor = monitor(monitorId);
        when(repository.findActiveByIdAndProjectIdForUpdate(monitorId, PROJECT_ID))
                .thenReturn(Optional.of(monitor));

        MonitorResponse response = service.pause(PROJECT_ID, monitorId);

        assertThat(response.enabled()).isFalse();
        assertThat(response.lifecycleVersion()).isEqualTo(1);
        verify(schedules).reconcile(monitorId);
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("UPDATE command_outbox")), eq(Timestamp.from(NOW)), eq(monitorId)
        );
    }

    @Test
    void resumesFromPendingWithANewLifecycle() {
        UUID monitorId = UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a");
        Monitor monitor = monitor(monitorId);
        monitor.pause(NOW.minusSeconds(30));
        when(repository.findActiveByIdAndProjectIdForUpdate(monitorId, PROJECT_ID))
                .thenReturn(Optional.of(monitor));

        MonitorResponse response = service.resume(PROJECT_ID, monitorId);

        assertThat(response.enabled()).isTrue();
        assertThat(response.lifecycleVersion()).isEqualTo(2);
        verify(schedules).reconcile(monitorId);
    }

    @Test
    void restoresAnArchivedMonitorAsPaused() {
        UUID monitorId = UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a");
        Monitor monitor = monitor(monitorId);
        monitor.archive(NOW.minusSeconds(30));
        when(repository.findByIdAndProjectIdForUpdate(monitorId, PROJECT_ID))
                .thenReturn(Optional.of(monitor));

        MonitorResponse response = service.restore(PROJECT_ID, monitorId);

        assertThat(response.archivedAt()).isNull();
        assertThat(response.enabled()).isFalse();
        assertThat(response.lifecycleVersion()).isEqualTo(2);
        verify(schedules).reconcile(monitorId);
    }

    @Test
    void rejectsRestoringAnActiveMonitorWithoutChangingItsSchedule() {
        UUID monitorId = UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a");
        when(repository.findByIdAndProjectIdForUpdate(monitorId, PROJECT_ID))
                .thenReturn(Optional.of(monitor(monitorId)));

        assertThatThrownBy(() -> service.restore(PROJECT_ID, monitorId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Monitor is not archived");
        verifyNoInteractions(schedules, incidents, jdbcTemplate);
    }

    @Test
    void replacesConfigurationAndReschedulesAnActiveMonitor() {
        UUID monitorId = UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a");
        Monitor monitor = monitor(monitorId);
        when(repository.findActiveByIdAndProjectIdForUpdate(monitorId, PROJECT_ID))
                .thenReturn(Optional.of(monitor));
        CreateMonitorRequest request = new CreateMonitorRequest(
                "Renamed API", URI.create("https://example.com/ready"), 120, 4000, 204
        );

        MonitorResponse response = service.update(PROJECT_ID, monitorId, request);

        assertThat(response.name()).isEqualTo("Renamed API");
        assertThat(response.targetUrl()).isEqualTo("https://example.com/ready");
        assertThat(response.frequencySeconds()).isEqualTo(120);
        assertThat(response.lifecycleVersion()).isEqualTo(1);
        verify(schedules).reconcile(monitorId);
    }

    private static Monitor monitor(UUID id) {
        return new Monitor(
                id, PROJECT_ID, "Public API", MonitorType.HTTP,
                "https://example.com/health", null, null, null, null, null,
                60, 5000, 200, true, NOW.minusSeconds(300)
        );
    }
}
