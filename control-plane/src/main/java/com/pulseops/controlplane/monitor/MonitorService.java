package com.pulseops.controlplane.monitor;

import com.pulseops.controlplane.incident.IncidentLifecycleService;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MonitorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MonitorService.class);
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final MonitorRepository repository;
    private final MonitorScheduleCoordinator schedules;
    private final IncidentLifecycleService incidents;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public MonitorService(
            MonitorRepository repository,
            MonitorScheduleCoordinator schedules,
            IncidentLifecycleService incidents,
            JdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.repository = repository;
        this.schedules = schedules;
        this.incidents = incidents;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public MonitorResponse create(UUID projectId, CreateMonitorRequest request) {
        MonitorType type = request.type() == null ? MonitorType.HTTP : request.type();
        validateTarget(request, type);
        NormalizedConfiguration configuration = normalizeConfiguration(request, type);
        Instant now = clock.instant();
        Monitor monitor = new Monitor(
                UUID.randomUUID(),
                projectId,
                request.name().trim(),
                type,
                configuration.targetUrl(),
                configuration.host(),
                configuration.port(),
                configuration.dnsRecordType(),
                configuration.expectedValue(),
                configuration.tlsExpiryWarningDays(),
                request.frequencySeconds(),
                request.timeoutMs(),
                configuration.expectedStatus(),
                true,
                now
        );
        Monitor saved = repository.save(monitor);
        afterCommit(() -> schedules.reconcile(saved.getId()));
        return MonitorResponse.from(saved);
    }

    public List<MonitorResponse> findAll(UUID projectId, boolean includeArchived) {
        List<Monitor> monitors = includeArchived
                ? repository.findAllByProjectIdOrderByCreatedAtDesc(projectId)
                : repository.findAllByProjectIdAndArchivedAtIsNullOrderByCreatedAtDesc(projectId);
        return monitors.stream()
                .map(MonitorResponse::from)
                .toList();
    }

    public MonitorResponse findById(UUID projectId, UUID id) {
        return MonitorResponse.from(repository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new MonitorNotFoundException(id)));
    }

    @Transactional
    public MonitorResponse update(UUID projectId, UUID id, CreateMonitorRequest request) {
        Monitor monitor = getActiveProjectForUpdate(projectId, id);
        MonitorType type = request.type() == null ? MonitorType.HTTP : request.type();
        validateTarget(request, type);
        NormalizedConfiguration configuration = normalizeConfiguration(request, type);
        Instant now = clock.instant();
        monitor.updateConfiguration(
                request.name().trim(), type, configuration.targetUrl(), configuration.host(),
                configuration.port(), configuration.dnsRecordType(), configuration.expectedValue(),
                configuration.tlsExpiryWarningDays(), request.frequencySeconds(), request.timeoutMs(),
                configuration.expectedStatus(), now
        );
        repository.save(monitor);
        invalidatePendingAndState(monitor, now);
        if (monitor.isEnabled()) {
            afterCommit(() -> schedules.reconcile(monitor.getId()));
        }
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public MonitorResponse pause(UUID projectId, UUID id) {
        Monitor monitor = getActiveProjectForUpdate(projectId, id);
        Instant now = clock.instant();
        if (monitor.pause(now)) {
            repository.save(monitor);
            invalidatePendingAndState(monitor, now);
        }
        afterCommit(() -> schedules.reconcile(monitor.getId()));
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public MonitorResponse resume(UUID projectId, UUID id) {
        Monitor monitor = getActiveProjectForUpdate(projectId, id);
        Instant now = clock.instant();
        if (monitor.resume(now)) {
            repository.save(monitor);
            invalidatePendingAndState(monitor, now);
        }
        afterCommit(() -> schedules.reconcile(monitor.getId()));
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public void archive(UUID projectId, UUID id) {
        Monitor monitor = getActiveProjectForUpdate(projectId, id);
        Instant now = clock.instant();
        monitor.archive(now);
        repository.save(monitor);
        invalidatePendingAndState(monitor, now);
        afterCommit(() -> schedules.reconcile(monitor.getId()));
    }

    @Transactional
    public MonitorResponse restore(UUID projectId, UUID id) {
        Monitor monitor = repository.findByIdAndProjectIdForUpdate(id, projectId)
                .orElseThrow(() -> new MonitorNotFoundException(id));
        Instant now = clock.instant();
        if (!monitor.restore(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Monitor is not archived");
        }
        repository.save(monitor);
        invalidatePendingAndState(monitor, now);
        afterCommit(() -> schedules.reconcile(monitor.getId()));
        return MonitorResponse.from(monitor);
    }

    private void invalidatePendingAndState(Monitor monitor, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        jdbcTemplate.update("""
                UPDATE command_outbox
                SET cancelled_at = ?, claim_token = NULL, claimed_until = NULL
                WHERE monitor_id = ? AND published_at IS NULL AND cancelled_at IS NULL
                """, timestamp, monitor.getId());
        jdbcTemplate.update("""
                INSERT INTO monitor_states (
                    monitor_id, status, consecutive_failures, consecutive_successes, updated_at
                ) VALUES (?, 'PENDING', 0, 0, ?)
                ON CONFLICT (monitor_id) DO UPDATE SET
                    status = 'PENDING', consecutive_failures = 0,
                    consecutive_successes = 0, updated_at = EXCLUDED.updated_at
                """, monitor.getId(), timestamp);
        incidents.resolveOpen(monitor.getId(), monitor.getName(), now);
    }

    public void requireProjectHistory(UUID projectId, UUID id) {
        repository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new MonitorNotFoundException(id));
    }

    public MonitorResponse findForBackground(UUID id) {
        return MonitorResponse.from(getRequiredGlobal(id));
    }

    public MonitorResponse findForBackgroundForUpdate(UUID id) {
        return MonitorResponse.from(repository.findByIdForUpdate(id)
                .orElseThrow(() -> new MonitorNotFoundException(id)));
    }

    private Monitor getActiveProjectForUpdate(UUID projectId, UUID id) {
        return repository.findActiveByIdAndProjectIdForUpdate(id, projectId)
                .orElseThrow(() -> new MonitorNotFoundException(id));
    }

    private Monitor getRequiredGlobal(UUID id) {
        return repository.findById(id).orElseThrow(() -> new MonitorNotFoundException(id));
    }

    private void validateTarget(CreateMonitorRequest request, MonitorType type) {
        if (request.timeoutMs() >= request.frequencySeconds() * 1000L) {
            throw new InvalidTargetException("Timeout must be shorter than the monitor frequency");
        }
        switch (type) {
            case HTTP -> validateHttp(request);
            case TCP -> validateHostAndPort(request, false);
            case DNS -> validateDns(request);
            case TLS -> validateHostAndPort(request, true);
        }
    }

    private void validateHttp(CreateMonitorRequest request) {
        if (request.targetUrl() == null) {
            throw new InvalidTargetException("HTTP monitors require a target URL");
        }
        String scheme = request.targetUrl().getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidTargetException("Only HTTP and HTTPS targets are supported");
        }
        if (request.targetUrl().getHost() == null) {
            throw new InvalidTargetException("Target URL must include a host");
        }
    }

    private void validateHostAndPort(CreateMonitorRequest request, boolean portOptional) {
        if (normalize(request.host()) == null) {
            throw new InvalidTargetException("This monitor type requires a host");
        }
        if (!portOptional && request.port() == null) {
            throw new InvalidTargetException("TCP monitors require a port");
        }
    }

    private void validateDns(CreateMonitorRequest request) {
        if (normalize(request.host()) == null) {
            throw new InvalidTargetException("DNS monitors require a hostname");
        }
        String recordType = normalize(request.dnsRecordType());
        if (recordType != null && !Set.of("A", "AAAA", "CNAME", "TXT").contains(recordType.toUpperCase())) {
            throw new InvalidTargetException("DNS record type must be A, AAAA, CNAME, or TXT");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private NormalizedConfiguration normalizeConfiguration(CreateMonitorRequest request, MonitorType type) {
        String host = normalize(request.host());
        Integer port = request.port();
        String recordType = normalize(request.dnsRecordType());
        Integer expectedStatus = request.expectedStatus();
        Integer warningDays = request.tlsExpiryWarningDays();
        if (type == MonitorType.HTTP && expectedStatus == null) {
            expectedStatus = 200;
        }
        if (type == MonitorType.DNS && recordType == null) {
            recordType = "A";
        }
        if (type == MonitorType.TLS) {
            port = port == null ? 443 : port;
            warningDays = warningDays == null ? 30 : warningDays;
        }
        return new NormalizedConfiguration(
                request.targetUrl() == null ? null : request.targetUrl().toASCIIString(),
                host,
                port,
                recordType == null ? null : recordType.toUpperCase(),
                normalize(request.expectedValue()),
                warningDays,
                expectedStatus
        );
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runScheduleAction(action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runScheduleAction(action);
            }
        });
    }

    private static void runScheduleAction(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOGGER.error("Monitor schedule reconciliation failed; the periodic reconciler will retry", exception);
        }
    }

    private record NormalizedConfiguration(
            String targetUrl,
            String host,
            Integer port,
            String dnsRecordType,
            String expectedValue,
            Integer tlsExpiryWarningDays,
            Integer expectedStatus
    ) {
    }
}
