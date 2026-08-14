package com.pulseops.controlplane.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckCommandSerializationTest {

    @Test
    void serializesScheduledAtAsIso8601Text() throws Exception {
        var command = new CheckCommand(
                UUID.fromString("54ca4c38-0474-49e8-b69e-19b54b68902c"),
                UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a"),
                "HTTP",
                "local",
                Instant.parse("2026-08-14T12:00:00Z"),
                5000,
                new CheckCommand.Configuration("https://example.com", 200)
        );
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        String json = objectMapper.writeValueAsString(command);

        assertThat(json).contains("\"scheduledAt\":\"2026-08-14T12:00:00Z\"");
    }
}
