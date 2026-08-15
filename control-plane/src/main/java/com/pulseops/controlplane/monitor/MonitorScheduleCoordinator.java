package com.pulseops.controlplane.monitor;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class MonitorScheduleCoordinator {

    private final MonitorRepository monitors;
    private final MonitorScheduleService schedules;

    MonitorScheduleCoordinator(MonitorRepository monitors, MonitorScheduleService schedules) {
        this.monitors = monitors;
        this.schedules = schedules;
    }

    List<UUID> findAllIds() {
        return monitors.findAllIds();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcile(UUID monitorId) {
        monitors.findByIdForUpdate(monitorId).ifPresent(schedules::reconcile);
    }
}
