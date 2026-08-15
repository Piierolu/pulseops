package com.pulseops.controlplane.monitor;

import java.time.Instant;
import java.util.UUID;

public record MonitorResponse(
        UUID id,
        UUID projectId,
        String name,
        MonitorType type,
        String targetUrl,
        String host,
        Integer port,
        String dnsRecordType,
        String expectedValue,
        Integer tlsExpiryWarningDays,
        int frequencySeconds,
        int timeoutMs,
        Integer expectedStatus,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        long lifecycleVersion
) {
    static MonitorResponse from(Monitor monitor) {
        return new MonitorResponse(
                monitor.getId(),
                monitor.getProjectId(),
                monitor.getName(),
                monitor.getType(),
                monitor.getTargetUrl(),
                monitor.getTargetHost(),
                monitor.getTargetPort(),
                monitor.getDnsRecordType(),
                monitor.getExpectedValue(),
                monitor.getTlsExpiryWarningDays(),
                monitor.getFrequencySeconds(),
                monitor.getTimeoutMs(),
                monitor.getExpectedStatus(),
                monitor.isEnabled(),
                monitor.getCreatedAt(),
                monitor.getUpdatedAt(),
                monitor.getArchivedAt(),
                monitor.getLifecycleVersion()
        );
    }
}
