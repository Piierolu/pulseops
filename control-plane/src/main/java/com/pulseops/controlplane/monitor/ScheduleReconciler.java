package com.pulseops.controlplane.monitor;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class ScheduleReconciler implements ApplicationRunner {

    private final MonitorService monitors;
    private final MonitorScheduleService schedules;

    ScheduleReconciler(MonitorService monitors, MonitorScheduleService schedules) {
        this.monitors = monitors;
        this.schedules = schedules;
    }

    @Override
    public void run(ApplicationArguments args) {
        monitors.findEnabled().forEach(schedules::schedule);
    }
}
