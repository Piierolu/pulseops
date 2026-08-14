package com.pulseops.controlplane.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AgentService {

    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(45);

    private final MonitoringAgentRepository repository;
    private final Clock clock;

    AgentService(MonitoringAgentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    void recordHeartbeat(AgentHeartbeatMessage message) {
        MonitoringAgent agent = repository.findById(message.agentId())
                .orElseGet(() -> MonitoringAgent.register(message));
        agent.update(message);
        repository.save(agent);
    }

    @Transactional(readOnly = true)
    List<AgentResponse> findAll() {
        Instant threshold = clock.instant().minus(ONLINE_WINDOW);
        return repository.findAllByOrderByLastSeenAtDesc().stream()
                .map(agent -> agent.toResponse(threshold))
                .toList();
    }
}
