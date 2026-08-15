package com.pulseops.controlplane.execution;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class CommandOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandOutboxPublisher.class);

    private final CommandOutboxRepository outbox;
    private final CheckCommandPublisher publisher;
    private final int batchSize;
    private final Duration claimLease;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    CommandOutboxPublisher(
            CommandOutboxRepository outbox,
            CheckCommandPublisher publisher,
            MeterRegistry meterRegistry,
            @Value("${pulseops.outbox.batch-size}") int batchSize,
            @Value("${pulseops.outbox.claim-lease}") Duration claimLease,
            @Value("${pulseops.outbox.initial-backoff}") Duration initialBackoff,
            @Value("${pulseops.outbox.max-backoff}") Duration maxBackoff
    ) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.batchSize = batchSize;
        Duration batchSendWindow = publisher.sendTimeout()
                .multipliedBy(Math.max(1, batchSize))
                .plusSeconds(5);
        this.claimLease = claimLease.compareTo(batchSendWindow) >= 0 ? claimLease : batchSendWindow;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.publishedCounter = meterRegistry.counter("pulseops.command.outbox.publish", "outcome", "success");
        this.failedCounter = meterRegistry.counter("pulseops.command.outbox.publish", "outcome", "failure");
    }

    @Scheduled(fixedDelayString = "${pulseops.outbox.poll-interval}")
    void publishReady() {
        for (PendingCommand command : outbox.claimBatch(batchSize, claimLease)) {
            try {
                publisher.publish(command);
                if (outbox.markPublished(command)) {
                    publishedCounter.increment();
                } else {
                    LOGGER.warn("Outbox claim ownership was lost after publishing execution {}", command.executionId());
                }
            } catch (RuntimeException exception) {
                boolean released = outbox.markFailed(
                        command,
                        retryDelay(command.attemptCount()),
                        exception.getMessage()
                );
                failedCounter.increment();
                if (!released) {
                    LOGGER.warn("Outbox claim ownership was lost after execution {} failed", command.executionId());
                }
                LOGGER.warn(
                        "Command outbox publication failed for execution {} on attempt {}",
                        command.executionId(),
                        command.attemptCount(),
                        exception
                );
            }
        }
    }

    private Duration retryDelay(int attemptCount) {
        long multiplier = 1L << Math.min(Math.max(0, attemptCount - 1), 20);
        long delayMillis;
        try {
            delayMillis = Math.multiplyExact(initialBackoff.toMillis(), multiplier);
        } catch (ArithmeticException ignored) {
            delayMillis = maxBackoff.toMillis();
        }
        return Duration.ofMillis(Math.min(delayMillis, maxBackoff.toMillis()));
    }
}
