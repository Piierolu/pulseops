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
    private final ExecutionReceiptRepository receipts;
    private final IncidentEvaluator incidentEvaluator;
    private final Counter receivedCounter;
    private final Counter duplicateCounter;

    CheckResultService(
            CheckResultRepository repository,
            ExecutionReceiptRepository receipts,
            IncidentEvaluator incidentEvaluator,
            MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.receipts = receipts;
        this.incidentEvaluator = incidentEvaluator;
        this.receivedCounter = Counter.builder("pulseops.checks.received")
                .description("Check results received from agents")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("pulseops.execution.duplicates")
                .description("Duplicate execution results ignored by the relational receipt")
                .register(meterRegistry);
    }

    @Transactional
    public void save(CheckResultMessage message) {
        if (!receipts.record(message)) {
            duplicateCounter.increment();
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
