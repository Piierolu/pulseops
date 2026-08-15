package com.pulseops.controlplane.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.controlplane.monitor.MonitorResponse;
import com.pulseops.controlplane.monitor.MonitorService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckDispatchService {

    private final MonitorService monitors;
    private final CommandOutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final String location;
    private final String commandsTopic;

    public CheckDispatchService(
            MonitorService monitors,
            CommandOutboxRepository outbox,
            ObjectMapper objectMapper,
            @Value("${pulseops.default-location}") String location,
            @Value("${pulseops.kafka.commands-topic}") String commandsTopic
    ) {
        this.monitors = monitors;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.location = location;
        this.commandsTopic = commandsTopic;
    }

    @Transactional
    public void enqueue(UUID monitorId, Instant scheduledAt) {
        MonitorResponse monitor = monitors.findById(monitorId);
        if (!monitor.enabled()) {
            return;
        }
        Instant scheduleSlot = scheduledAt.truncatedTo(ChronoUnit.MILLIS);
        UUID executionId = executionId(monitorId, location, scheduleSlot);
        CheckCommand command = new CheckCommand(
                executionId,
                monitor.id(),
                monitor.type().name(),
                location,
                scheduleSlot,
                monitor.timeoutMs(),
                new CheckCommand.Configuration(
                        monitor.targetUrl(),
                        monitor.expectedStatus(),
                        monitor.host(),
                        monitor.port(),
                        monitor.dnsRecordType(),
                        monitor.expectedValue(),
                        monitor.tlsExpiryWarningDays()
                )
        );
        try {
            outbox.enqueue(command, commandsTopic, objectMapper.writeValueAsString(command), TraceHeaders.capture());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize check command " + executionId, exception);
        }
    }

    static UUID executionId(UUID monitorId, String location, Instant scheduledAt) {
        String source = monitorId + ":" + location + ":" + scheduledAt.toEpochMilli();
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
}
