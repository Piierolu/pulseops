package com.pulseops.controlplane.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MonitoringAgentTest {

    @Test
    void reportsOnlineWithinHeartbeatWindow() {
        Instant heartbeatAt = Instant.parse("2026-08-14T12:00:00Z");
        MonitoringAgent agent = MonitoringAgent.register(new AgentHeartbeatMessage(
                "local-01", "local", "0.3.0", heartbeatAt
        ));

        AgentResponse response = agent.toResponse(heartbeatAt.minusSeconds(45));

        assertThat(response.status()).isEqualTo("ONLINE");
    }

    @Test
    void reportsOfflineOutsideHeartbeatWindow() {
        Instant heartbeatAt = Instant.parse("2026-08-14T12:00:00Z");
        MonitoringAgent agent = MonitoringAgent.register(new AgentHeartbeatMessage(
                "local-01", "local", "0.3.0", heartbeatAt
        ));

        AgentResponse response = agent.toResponse(heartbeatAt.plusSeconds(1));

        assertThat(response.status()).isEqualTo("OFFLINE");
    }
}
