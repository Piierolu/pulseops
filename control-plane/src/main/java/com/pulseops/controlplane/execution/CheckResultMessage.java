package com.pulseops.controlplane.execution;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CheckResultMessage(
        UUID executionId,
        UUID monitorId,
        String agentId,
        String location,
        String status,
        long latencyMs,
        Integer statusCode,
        String error,
        Map<String, Object> details,
        Instant checkedAt
) {
}
