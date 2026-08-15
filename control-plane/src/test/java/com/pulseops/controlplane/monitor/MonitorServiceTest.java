package com.pulseops.controlplane.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
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
    private MonitorScheduleService schedules;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MonitorService service;

    @BeforeEach
    void setUp() {
        service = new MonitorService(repository, schedules, jdbcTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
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
        verify(schedules).schedule(org.mockito.ArgumentMatchers.any(Monitor.class));
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
        when(repository.findByIdAndProjectIdAndArchivedAtIsNull(monitorId, otherProject))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(otherProject, monitorId))
                .isInstanceOf(MonitorNotFoundException.class);
    }

    @Test
    void archivesAndUnschedulesWithoutDeletingTheMonitor() {
        AtomicReference<Monitor> persisted = new AtomicReference<>();
        when(repository.save(org.mockito.ArgumentMatchers.any(Monitor.class))).thenAnswer(invocation -> {
            Monitor monitor = invocation.getArgument(0);
            persisted.set(monitor);
            return monitor;
        });
        MonitorResponse created = service.create(PROJECT_ID, new CreateMonitorRequest(
                "Public API", URI.create("https://example.com/health"), 60, 5000, 200
        ));
        when(repository.findActiveByIdAndProjectIdForUpdate(created.id(), PROJECT_ID))
                .thenReturn(Optional.of(persisted.get()));

        service.archive(PROJECT_ID, created.id());

        assertThat(persisted.get().isEnabled()).isFalse();
        assertThat(persisted.get().getArchivedAt()).isEqualTo(NOW);
        verify(schedules).unschedule(created.id());
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("UPDATE incidents")),
                eq(NOW), eq(NOW), eq(created.id())
        );
    }
}
