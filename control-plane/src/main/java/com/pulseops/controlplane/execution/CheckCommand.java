package com.pulseops.controlplane.execution;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record CheckCommand(
        UUID executionId,
        UUID monitorId,
        String type,
        String location,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant scheduledAt,
        int timeoutMs,
        Configuration configuration
) {
    public record Configuration(
            String url,
            Integer expectedStatus,
            String host,
            Integer port,
            String recordType,
            String expectedValue,
            Integer expiryWarningDays
    ) {
        public Configuration(String url, int expectedStatus) {
            this(url, expectedStatus, null, null, null, null, null);
        }
    }
}
