package com.pulseops.controlplane.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;

public record CreateMonitorRequest(
        @NotBlank @Size(max = 120) String name,
        MonitorType type,
        URI targetUrl,
        @Size(max = 253) String host,
        @Min(1) @Max(65535) Integer port,
        @Size(max = 10) String dnsRecordType,
        @Size(max = 2048) String expectedValue,
        @Min(1) @Max(365) Integer tlsExpiryWarningDays,
        @Min(10) @Max(86400) int frequencySeconds,
        @Min(100) @Max(60000) int timeoutMs,
        @Min(100) @Max(599) Integer expectedStatus
) {
    public CreateMonitorRequest(
            String name,
            URI targetUrl,
            int frequencySeconds,
            int timeoutMs,
            int expectedStatus
    ) {
        this(name, MonitorType.HTTP, targetUrl, null, null, null, null, null,
                frequencySeconds, timeoutMs, expectedStatus);
    }
}
