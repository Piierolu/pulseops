package com.pulseops.controlplane.reporting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OverviewResponse(
        Stats stats,
        List<MonitorSnapshot> monitors,
        Instant generatedAt
) {
    public record Stats(
            long totalMonitors,
            long upMonitors,
            long degradedMonitors,
            long downMonitors,
            long pendingMonitors,
            long openIncidents,
            long totalAgents,
            long onlineAgents,
            long checks24h,
            Double availability24h,
            double averageLatencyMs
    ) {
    }

    public record MonitorSnapshot(
            UUID id,
            String name,
            String type,
            String target,
            String status,
            String lastCheckStatus,
            Long lastLatencyMs,
            Instant lastCheckedAt,
            Double availability24h
    ) {
    }
}
