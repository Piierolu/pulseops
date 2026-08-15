package com.pulseops.controlplane.monitor;

import java.util.UUID;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.TriggerKey;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Service;

@Service
public class MonitorScheduleService {

    private static final String GROUP = "http-monitors";

    private final Scheduler scheduler;

    public MonitorScheduleService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void schedule(Monitor monitor) {
        JobKey jobKey = jobKey(monitor.getId());
        try {
            if (scheduler.checkExists(jobKey)) {
                scheduler.rescheduleJob(triggerKey(monitor.getId()), trigger(monitor));
                return;
            }
            scheduler.scheduleJob(job(monitor), trigger(monitor));
        } catch (ObjectAlreadyExistsException ignored) {
            // Another control-plane replica created the same stable schedule.
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Could not schedule monitor " + monitor.getId(), exception);
        }
    }

    public void ensureScheduled(Monitor monitor) {
        try {
            JobKey jobKey = jobKey(monitor.getId());
            TriggerKey triggerKey = triggerKey(monitor.getId());
            if (!scheduler.checkExists(jobKey)) {
                scheduler.scheduleJob(job(monitor), trigger(monitor));
                return;
            }
            org.quartz.Trigger existing = scheduler.getTrigger(triggerKey);
            if (existing == null) {
                scheduler.scheduleJob(trigger(monitor));
            } else if (!(existing instanceof SimpleTrigger simple)
                    || simple.getRepeatInterval() != monitor.getFrequencySeconds() * 1000L) {
                scheduler.rescheduleJob(triggerKey, trigger(monitor));
            }
        } catch (ObjectAlreadyExistsException ignored) {
            // Reconciliation is intentionally idempotent across replicas.
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Could not reconcile monitor " + monitor.getId(), exception);
        }
    }

    public void unschedule(UUID monitorId) {
        try {
            scheduler.deleteJob(jobKey(monitorId));
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Could not unschedule monitor " + monitorId, exception);
        }
    }

    public void reconcile(Monitor monitor) {
        if (monitor.isEnabled() && monitor.getArchivedAt() == null) {
            ensureScheduled(monitor);
        } else {
            unschedule(monitor.getId());
        }
    }

    private JobKey jobKey(UUID monitorId) {
        return JobKey.jobKey("monitor-" + monitorId, GROUP);
    }

    private TriggerKey triggerKey(UUID monitorId) {
        return TriggerKey.triggerKey("trigger-" + monitorId, GROUP);
    }

    private org.quartz.JobDetail job(Monitor monitor) {
        return JobBuilder.newJob(CheckDispatchJob.class)
                .withIdentity(jobKey(monitor.getId()))
                .usingJobData(CheckDispatchJob.MONITOR_ID, monitor.getId().toString())
                .requestRecovery()
                .build();
    }

    private org.quartz.Trigger trigger(Monitor monitor) {
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(monitor.getId()))
                .forJob(jobKey(monitor.getId()))
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(monitor.getFrequencySeconds())
                        .repeatForever()
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .build();
    }
}
