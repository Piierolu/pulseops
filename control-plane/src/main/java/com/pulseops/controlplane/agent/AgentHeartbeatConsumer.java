package com.pulseops.controlplane.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class AgentHeartbeatConsumer {

    private final AgentService service;
    private final ObjectMapper objectMapper;

    AgentHeartbeatConsumer(AgentService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${pulseops.kafka.heartbeats-topic}",
            groupId = "pulseops-agent-registry"
    )
    void consume(String payload) {
        try {
            service.recordHeartbeat(objectMapper.readValue(payload, AgentHeartbeatMessage.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid agent heartbeat payload", exception);
        }
    }
}
