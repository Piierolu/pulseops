package com.pulseops.controlplane.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
class CheckCommandPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Duration sendTimeout;

    CheckCommandPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${pulseops.kafka.send-timeout:10s}") Duration sendTimeout
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.sendTimeout = sendTimeout;
    }

    void publish(PendingCommand pending) {
        CheckCommand command = deserialize(pending);
        try (Scope ignored = pending.traceHeaders().restore().makeCurrent()) {
            kafkaTemplate.send(new ProducerRecord<>(
                            pending.destinationTopic(),
                            pending.messageKey(),
                            command
                    ))
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not publish check command " + pending.executionId(), exception);
        }
    }

    Duration sendTimeout() {
        return sendTimeout;
    }

    private CheckCommand deserialize(PendingCommand pending) {
        try {
            return objectMapper.readValue(pending.payload(), CheckCommand.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid outbox command " + pending.executionId(), exception);
        }
    }
}
