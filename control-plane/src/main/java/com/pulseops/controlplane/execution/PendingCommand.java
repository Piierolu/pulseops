package com.pulseops.controlplane.execution;

import java.util.UUID;

record PendingCommand(
        UUID executionId,
        String destinationTopic,
        String messageKey,
        String payload,
        TraceHeaders traceHeaders,
        UUID claimToken,
        int attemptCount
) {
}
