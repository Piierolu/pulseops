package com.pulseops.controlplane.monitor;

import java.util.UUID;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
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
                scheduler.deleteJob(jobKey);
            }
            var job = JobBuilder.newJob(CheckDispatchJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(CheckDispatchJob.MONITOR_ID, monitor.getId().toString())
                    .build();
            var trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger-" + monitor.getId(), GROUP)
                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInSeconds(monitor.getFrequencySeconds())
                            .repeatForever()
                            .withMisfireHandlingInstructionNextWithRemainingCount())
                    .build();
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Could not schedule monitor " + monitor.getId(), exception);
        }
    }

    public void unschedule(UUID monitorId) {
        try {
            scheduler.deleteJob(jobKey(monitorId));
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Could not unschedule monitor " + monitorId, exception);
        }
    }

    private JobKey jobKey(UUID monitorId) {
        return JobKey.jobKey("monitor-" + monitorId, GROUP);
    }
}
