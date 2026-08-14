package com.pulseops.controlplane.execution;

import com.pulseops.controlplane.incident.IncidentEvaluator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CheckResultService {

    private final CheckResultRepository repository;
    private final IncidentEvaluator incidentEvaluator;
    private final Counter receivedCounter;

    CheckResultService(
            CheckResultRepository repository,
            IncidentEvaluator incidentEvaluator,
            MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.incidentEvaluator = incidentEvaluator;
        this.receivedCounter = Counter.builder("pulseops.checks.received")
                .description("Check results received from agents")
                .register(meterRegistry);
    }

    @Transactional
    public void save(CheckResultMessage message) {
        if (repository.existsByExecutionId(message.executionId())) {
            return;
        }
        repository.save(CheckResult.from(message));
        incidentEvaluator.evaluate(message);
        receivedCounter.increment();
    }

    @Transactional(readOnly = true)
    public List<CheckResultResponse> findRecent(UUID monitorId, int limit) {
        return repository.findByMonitorIdOrderByCheckedAtDesc(monitorId, PageRequest.of(0, limit)).stream()
                .map(CheckResult::toResponse)
                .toList();
    }
}
