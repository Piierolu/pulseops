package com.pulseops.controlplane.execution;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulseops.controlplane.incident.IncidentEvaluator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckResultServiceTest {

    @Mock
    private CheckResultRepository repository;

    @Mock
    private ExecutionReceiptRepository receipts;

    @Mock
    private IncidentEvaluator incidents;

    private CheckResultService service;
    private CheckResultMessage message;

    @BeforeEach
    void setUp() {
        service = new CheckResultService(repository, receipts, incidents, new SimpleMeterRegistry());
        message = new CheckResultMessage(
                UUID.fromString("54ca4c38-0474-49e8-b69e-19b54b68902c"),
                UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a"),
                "local-01",
                "local",
                "SUCCESS",
                12,
                200,
                null,
                Map.of(),
                Instant.parse("2026-08-14T12:00:00Z")
        );
    }

    @Test
    void ignoresResultWhenExecutionReceiptAlreadyExists() {
        when(receipts.record(message)).thenReturn(false);

        service.save(message);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(incidents, never()).evaluate(message);
    }

    @Test
    void persistsAndEvaluatesOnlyAfterClaimingExecutionReceipt() {
        when(receipts.record(message)).thenReturn(true);

        service.save(message);

        verify(repository).save(org.mockito.ArgumentMatchers.any());
        verify(incidents).evaluate(message);
    }
}
