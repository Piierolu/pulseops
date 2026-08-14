package com.pulseops.controlplane.agent;

import java.time.Instant;

public record AgentResponse(
        String agentId,
        String location,
        String version,
        String status,
        Instant firstSeenAt,
        Instant lastSeenAt
) {
}
