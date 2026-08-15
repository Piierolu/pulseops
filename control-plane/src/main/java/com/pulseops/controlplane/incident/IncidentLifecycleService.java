package com.pulseops.controlplane.incident;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IncidentLifecycleService {

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher events;
    private final Counter resolvedCounter;

    IncidentLifecycleService(
            JdbcTemplate jdbcTemplate,
            ApplicationEventPublisher events,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.events = events;
        this.resolvedCounter = Counter.builder("pulseops.incidents.resolved").register(meterRegistry);
    }

    public void resolveOpen(UUID monitorId, String monitorName, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        var resolvedIds = jdbcTemplate.queryForList("""
                UPDATE incidents
                SET status = 'RESOLVED', resolved_at = ?, updated_at = ?
                WHERE monitor_id = ? AND status = 'OPEN'
                RETURNING id
                """, UUID.class, timestamp, timestamp, monitorId);
        resolvedIds.forEach(incidentId -> events.publishEvent(new IncidentChangedEvent(
                incidentId, monitorId, monitorName, IncidentStatus.RESOLVED, now
        )));
        resolvedCounter.increment(resolvedIds.size());
    }
}
