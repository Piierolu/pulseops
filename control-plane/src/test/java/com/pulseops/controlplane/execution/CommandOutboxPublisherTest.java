package com.pulseops.controlplane.execution;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommandOutboxPublisherTest {

    @Mock
    private CommandOutboxRepository outbox;

    @Mock
    private CheckCommandPublisher publisher;

    @Test
    void extendsClaimLeaseToCoverWorstCaseBatchSendTime() {
        when(publisher.sendTimeout()).thenReturn(Duration.ofSeconds(10));
        when(outbox.claimBatch(20, Duration.ofSeconds(205))).thenReturn(List.of());

        CommandOutboxPublisher outboxPublisher = new CommandOutboxPublisher(
                outbox,
                publisher,
                new SimpleMeterRegistry(),
                20,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofMinutes(1)
        );

        outboxPublisher.publishReady();

        verify(outbox).claimBatch(20, Duration.ofSeconds(205));
    }
}
