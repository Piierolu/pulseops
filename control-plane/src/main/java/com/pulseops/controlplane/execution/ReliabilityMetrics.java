package com.pulseops.controlplane.execution;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;

@Component
class ReliabilityMetrics {

    ReliabilityMetrics(
            MeterRegistry meterRegistry,
            CommandOutboxRepository outbox,
            Scheduler scheduler
    ) {
        Gauge.builder("pulseops.command.outbox.pending", outbox, CommandOutboxRepository::pendingCount)
                .description("Commands waiting for confirmed Kafka publication")
                .register(meterRegistry);
        Gauge.builder(
                        "pulseops.command.outbox.oldest.pending.age.seconds",
                        outbox,
                        CommandOutboxRepository::oldestPendingAgeSeconds
                )
                .description("Age in seconds of the oldest unpublished command")
                .register(meterRegistry);
        Gauge.builder("pulseops.quartz.scheduler.running", scheduler, ReliabilityMetrics::schedulerRunning)
                .description("Whether the clustered Quartz scheduler is running")
                .register(meterRegistry);
    }

    private static double schedulerRunning(Scheduler scheduler) {
        try {
            return scheduler.isStarted() && !scheduler.isInStandbyMode() && !scheduler.isShutdown() ? 1 : 0;
        } catch (SchedulerException exception) {
            return 0;
        }
    }
}
