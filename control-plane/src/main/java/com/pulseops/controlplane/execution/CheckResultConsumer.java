package com.pulseops.controlplane.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class CheckResultConsumer {

    private final CheckResultService service;
    private final ObjectMapper objectMapper;
    private final Timer processingTimer;

    CheckResultConsumer(CheckResultService service, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.processingTimer = Timer.builder("pulseops.kafka.result.processing")
                .description("Time spent processing check results")
                .register(meterRegistry);
    }

    @KafkaListener(topics = "${pulseops.kafka.results-topic}", groupId = "pulseops-control-plane")
    void consume(String payload) {
        CheckResultMessage message = read(payload);
        processingTimer.record(() -> service.save(message));
    }

    private CheckResultMessage read(String payload) {
        try {
            return objectMapper.readValue(payload, CheckResultMessage.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid check result payload", exception);
        }
    }
}
