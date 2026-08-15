package com.pulseops.controlplane.monitor;

import com.pulseops.controlplane.execution.CheckDispatchService;
import java.util.UUID;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class CheckDispatchJob implements Job {

    static final String MONITOR_ID = "monitorId";

    @Autowired
    private CheckDispatchService dispatchService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String monitorId = context.getMergedJobDataMap().getString(MONITOR_ID);
        try {
            dispatchService.enqueue(
                    UUID.fromString(monitorId),
                    context.getScheduledFireTime().toInstant()
            );
        } catch (RuntimeException exception) {
            JobExecutionException jobException = new JobExecutionException(exception);
            jobException.setRefireImmediately(context.getRefireCount() < 2);
            throw jobException;
        }
    }
}
