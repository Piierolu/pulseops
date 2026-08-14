package com.pulseops.controlplane.reporting;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OverviewService {

    private static final String STATS_SQL = """
            SELECT
                (SELECT count(*) FROM monitors WHERE enabled = true) AS total_monitors,
                (SELECT count(*) FROM monitor_states s JOIN monitors m ON m.id = s.monitor_id
                    WHERE m.enabled = true AND s.status = 'UP') AS up_monitors,
                (SELECT count(*) FROM monitor_states s JOIN monitors m ON m.id = s.monitor_id
                    WHERE m.enabled = true AND s.status IN ('DEGRADED', 'RECOVERING')) AS degraded_monitors,
                (SELECT count(*) FROM monitor_states s JOIN monitors m ON m.id = s.monitor_id
                    WHERE m.enabled = true AND s.status = 'DOWN') AS down_monitors,
                (SELECT count(*) FROM monitors m LEFT JOIN monitor_states s ON s.monitor_id = m.id
                    WHERE m.enabled = true AND COALESCE(s.status, 'PENDING') = 'PENDING') AS pending_monitors,
                (SELECT count(*) FROM incidents WHERE status = 'OPEN') AS open_incidents,
                (SELECT count(*) FROM monitoring_agents) AS total_agents,
                (SELECT count(*) FROM monitoring_agents
                    WHERE last_seen_at >= now() - interval '45 seconds') AS online_agents,
                count(r.id) AS checks_24h,
                CASE WHEN count(r.id) = 0 THEN NULL
                    ELSE round(100.0 * count(*) FILTER (WHERE r.status = 'SUCCESS') / count(r.id), 2)
                END AS availability_24h,
                COALESCE(round(avg(r.latency_ms)::numeric, 1), 0) AS average_latency_ms
            FROM check_results r
            WHERE r.checked_at >= now() - interval '24 hours'
            """;

    private static final String MONITORS_SQL = """
            SELECT
                m.id,
                m.name,
                m.monitor_type,
                CASE m.monitor_type
                    WHEN 'HTTP' THEN m.target_url
                    WHEN 'DNS' THEN m.target_host || ' [' || COALESCE(m.dns_record_type, 'A') || ']'
                    ELSE m.target_host || ':' || m.target_port
                END AS target,
                COALESCE(s.status, 'PENDING') AS status,
                latest.status AS last_check_status,
                latest.latency_ms AS last_latency_ms,
                latest.checked_at AS last_checked_at,
                history.availability_24h
            FROM monitors m
            LEFT JOIN monitor_states s ON s.monitor_id = m.id
            LEFT JOIN LATERAL (
                SELECT r.status, r.latency_ms, r.checked_at
                FROM check_results r
                WHERE r.monitor_id = m.id
                ORDER BY r.checked_at DESC
                LIMIT 1
            ) latest ON true
            LEFT JOIN LATERAL (
                SELECT CASE WHEN count(*) = 0 THEN NULL
                    ELSE round(100.0 * count(*) FILTER (WHERE r.status = 'SUCCESS') / count(*), 2)
                END AS availability_24h
                FROM check_results r
                WHERE r.monitor_id = m.id
                  AND r.checked_at >= now() - interval '24 hours'
            ) history ON true
            WHERE m.enabled = true
            ORDER BY m.created_at DESC
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    OverviewService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    OverviewResponse getOverview() {
        OverviewResponse.Stats stats = jdbcTemplate.queryForObject(STATS_SQL, (resultSet, rowNumber) ->
                new OverviewResponse.Stats(
                        resultSet.getLong("total_monitors"),
                        resultSet.getLong("up_monitors"),
                        resultSet.getLong("degraded_monitors"),
                        resultSet.getLong("down_monitors"),
                        resultSet.getLong("pending_monitors"),
                        resultSet.getLong("open_incidents"),
                        resultSet.getLong("total_agents"),
                        resultSet.getLong("online_agents"),
                        resultSet.getLong("checks_24h"),
                        nullableDouble(resultSet.getObject("availability_24h")),
                        resultSet.getDouble("average_latency_ms")
                ));
        List<OverviewResponse.MonitorSnapshot> monitors = jdbcTemplate.query(MONITORS_SQL, (resultSet, rowNumber) -> {
            Timestamp checkedAt = resultSet.getTimestamp("last_checked_at");
            Number latency = (Number) resultSet.getObject("last_latency_ms");
            return new OverviewResponse.MonitorSnapshot(
                    resultSet.getObject("id", java.util.UUID.class),
                    resultSet.getString("name"),
                    resultSet.getString("monitor_type"),
                    resultSet.getString("target"),
                    resultSet.getString("status"),
                    resultSet.getString("last_check_status"),
                    latency == null ? null : latency.longValue(),
                    checkedAt == null ? null : checkedAt.toInstant(),
                    nullableDouble(resultSet.getObject("availability_24h"))
            );
        });
        return new OverviewResponse(stats, monitors, clock.instant());
    }

    private static Double nullableDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }
}
