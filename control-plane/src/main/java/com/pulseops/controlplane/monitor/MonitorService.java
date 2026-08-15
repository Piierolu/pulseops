package com.pulseops.controlplane.monitor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorService {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final MonitorRepository repository;
    private final MonitorScheduleService schedules;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public MonitorService(
            MonitorRepository repository,
            MonitorScheduleService schedules,
            JdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.repository = repository;
        this.schedules = schedules;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public MonitorResponse create(UUID projectId, CreateMonitorRequest request) {
        MonitorType type = request.type() == null ? MonitorType.HTTP : request.type();
        validateTarget(request, type);
        Instant now = clock.instant();
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
        Monitor monitor = new Monitor(
                UUID.randomUUID(),
                projectId,
                request.name().trim(),
                type,
                request.targetUrl() == null ? null : request.targetUrl().toASCIIString(),
                host,
                port,
                recordType == null ? null : recordType.toUpperCase(),
                normalize(request.expectedValue()),
                warningDays,
                request.frequencySeconds(),
                request.timeoutMs(),
                expectedStatus,
                true,
                now
        );
        Monitor saved = repository.save(monitor);
        schedules.schedule(saved);
        return MonitorResponse.from(saved);
    }

    public List<MonitorResponse> findAll(UUID projectId) {
        return repository.findAllByProjectIdAndArchivedAtIsNullOrderByCreatedAtDesc(projectId).stream()
                .map(MonitorResponse::from)
                .toList();
    }

    public MonitorResponse findById(UUID projectId, UUID id) {
        return MonitorResponse.from(getProjectRequired(projectId, id));
    }

    @Transactional
    public void archive(UUID projectId, UUID id) {
        Monitor monitor = repository.findActiveByIdAndProjectIdForUpdate(id, projectId)
                .orElseThrow(() -> new MonitorNotFoundException(id));
        schedules.unschedule(monitor.getId());
        monitor.archive(clock.instant());
        repository.save(monitor);
        jdbcTemplate.update("""
                UPDATE incidents
                SET status = 'RESOLVED', resolved_at = ?, updated_at = ?
                WHERE monitor_id = ? AND status = 'OPEN'
                """, monitor.getArchivedAt(), monitor.getArchivedAt(), monitor.getId());
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

    private Monitor getProjectRequired(UUID projectId, UUID id) {
        return repository.findByIdAndProjectIdAndArchivedAtIsNull(id, projectId)
                .orElseThrow(() -> new MonitorNotFoundException(id));
    }

    private Monitor getRequiredGlobal(UUID id) {
        return repository.findById(id).orElseThrow(() -> new MonitorNotFoundException(id));
    }

    List<Monitor> findEnabled() {
        return repository.findAllByEnabledTrueAndArchivedAtIsNull();
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
}
