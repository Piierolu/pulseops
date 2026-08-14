package com.pulseops.controlplane.incident;

final class IncidentStateMachine {

    private static final int FAILURE_THRESHOLD = 3;
    private static final int RECOVERY_THRESHOLD = 2;

    private IncidentStateMachine() {
    }

    static Outcome evaluate(CurrentState current, boolean successful) {
        if (successful) {
            int successes = Math.min(current.consecutiveSuccesses() + 1, RECOVERY_THRESHOLD);
            boolean recovering = current.status() == MonitorStatus.DOWN
                    || current.status() == MonitorStatus.RECOVERING;
            if (recovering && successes < RECOVERY_THRESHOLD) {
                return new Outcome(MonitorStatus.RECOVERING, 0, successes, false, false);
            }
            boolean resolveIncident = recovering && successes >= RECOVERY_THRESHOLD;
            return new Outcome(MonitorStatus.UP, 0, successes, false, resolveIncident);
        }

        int failures = Math.min(current.consecutiveFailures() + 1, FAILURE_THRESHOLD);
        if (failures >= FAILURE_THRESHOLD) {
            boolean openIncident = current.status() != MonitorStatus.DOWN
                    && current.status() != MonitorStatus.RECOVERING;
            return new Outcome(MonitorStatus.DOWN, failures, 0, openIncident, false);
        }
        if (current.status() == MonitorStatus.DOWN || current.status() == MonitorStatus.RECOVERING) {
            return new Outcome(MonitorStatus.DOWN, failures, 0, false, false);
        }
        return new Outcome(MonitorStatus.DEGRADED, failures, 0, false, false);
    }

    record CurrentState(MonitorStatus status, int consecutiveFailures, int consecutiveSuccesses) {
    }

    record Outcome(
            MonitorStatus status,
            int consecutiveFailures,
            int consecutiveSuccesses,
            boolean openIncident,
            boolean resolveIncident
    ) {
    }
}
