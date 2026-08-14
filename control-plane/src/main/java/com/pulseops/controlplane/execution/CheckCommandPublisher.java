package com.pulseops.controlplane.execution;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
class CheckCommandPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;
    private final Duration sendTimeout;

    CheckCommandPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${pulseops.kafka.commands-topic}") String topic,
            @Value("${pulseops.kafka.send-timeout:10s}") Duration sendTimeout
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.sendTimeout = sendTimeout;
    }

    void publish(CheckCommand command) {
        try {
            kafkaTemplate.send(topic, command.executionId().toString(), command)
                    .get(sendTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not publish check command " + command.executionId(), exception);
        }
    }
}
