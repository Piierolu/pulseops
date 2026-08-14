package com.pulseops.controlplane.monitor;

import com.pulseops.controlplane.execution.CheckDispatchService;
import java.util.UUID;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class CheckDispatchJob implements Job {

    static final String MONITOR_ID = "monitorId";

    @Autowired
    private CheckDispatchService dispatchService;

    @Override
    public void execute(JobExecutionContext context) {
        String monitorId = context.getMergedJobDataMap().getString(MONITOR_ID);
        dispatchService.dispatch(UUID.fromString(monitorId));
    }
}
