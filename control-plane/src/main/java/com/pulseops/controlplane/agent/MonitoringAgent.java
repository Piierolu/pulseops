package com.pulseops.controlplane.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "monitoring_agents")
class MonitoringAgent {

    @Id
    @Column(name = "agent_id", length = 120)
    private String agentId;

    @Column(nullable = false, length = 80)
    private String location;

    @Column(nullable = false, length = 40)
    private String version;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected MonitoringAgent() {
    }

    static MonitoringAgent register(AgentHeartbeatMessage message) {
        MonitoringAgent agent = new MonitoringAgent();
        agent.agentId = message.agentId();
        agent.firstSeenAt = message.sentAt();
        agent.update(message);
        return agent;
    }

    void update(AgentHeartbeatMessage message) {
        location = message.location();
        version = message.version();
        lastSeenAt = message.sentAt();
    }

    AgentResponse toResponse(Instant onlineThreshold) {
        String status = lastSeenAt.isAfter(onlineThreshold) ? "ONLINE" : "OFFLINE";
        return new AgentResponse(agentId, location, version, status, firstSeenAt, lastSeenAt);
    }
}
