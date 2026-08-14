package com.pulseops.controlplane.execution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckResultConsumerTest {

    @Mock
    private CheckResultService service;

    private CheckResultConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new CheckResultConsumer(service, objectMapper, new SimpleMeterRegistry());
    }

    @Test
    void parsesValidPayloadBeforeSaving() {
        consumer.consume("""
                {
                  "executionId": "54ca4c38-0474-49e8-b69e-19b54b68902c",
                  "monitorId": "828289fc-9bf1-4133-a822-d42942c0f38a",
                  "agentId": "local-01",
                  "location": "local",
                  "status": "SUCCESS",
                  "latencyMs": 12,
                  "statusCode": 200,
                  "error": null,
                  "details": {},
                  "checkedAt": "2026-08-14T12:00:00Z"
                }
                """);

        verify(service).save(argThat(message ->
                message.agentId().equals("local-01") && message.latencyMs() == 12
        ));
    }

    @Test
    void rejectsMalformedPayloadForKafkaRecovery() {
        assertThatThrownBy(() -> consumer.consume("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid check result payload");
    }
}
