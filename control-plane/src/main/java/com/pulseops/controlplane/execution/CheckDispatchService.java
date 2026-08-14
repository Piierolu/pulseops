package com.pulseops.controlplane.execution;

import com.pulseops.controlplane.monitor.MonitorResponse;
import com.pulseops.controlplane.monitor.MonitorService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CheckDispatchService {

    private final MonitorService monitors;
    private final CheckCommandPublisher publisher;
    private final Clock clock;
    private final String location;

    public CheckDispatchService(
            MonitorService monitors,
            CheckCommandPublisher publisher,
            Clock clock,
            @Value("${pulseops.default-location}") String location
    ) {
        this.monitors = monitors;
        this.publisher = publisher;
        this.clock = clock;
        this.location = location;
    }

    public void dispatch(UUID monitorId) {
        MonitorResponse monitor = monitors.findById(monitorId);
        if (!monitor.enabled()) {
            return;
        }
        publisher.publish(new CheckCommand(
                UUID.randomUUID(),
                monitor.id(),
                monitor.type().name(),
                location,
                clock.instant(),
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
        ));
    }
}
