package com.pulseops.controlplane.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ScheduleReconciler implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduleReconciler.class);

    private final MonitorScheduleCoordinator schedules;

    ScheduleReconciler(MonitorScheduleCoordinator schedules) {
        this.schedules = schedules;
    }

    @Override
    public void run(ApplicationArguments args) {
        reconcile();
    }

    @Scheduled(fixedDelayString = "${pulseops.schedule-reconcile-interval:60000}")
    void reconcile() {
        schedules.findAllIds().forEach(monitorId -> {
            try {
                schedules.reconcile(monitorId);
            } catch (RuntimeException exception) {
                LOGGER.error("Could not reconcile monitor schedule {}", monitorId, exception);
            }
        });
    }
}
