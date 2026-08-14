package com.pulseops.controlplane.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record AgentHeartbeatMessage(
        String agentId,
        String location,
        String version,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant sentAt
) {
}
