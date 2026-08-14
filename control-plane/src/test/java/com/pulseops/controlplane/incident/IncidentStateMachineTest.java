package com.pulseops.controlplane.incident;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IncidentStateMachineTest {

    @Test
    void degradesAfterTheFirstFailure() {
        var outcome = evaluate(MonitorStatus.UP, 0, 2, false);

        assertThat(outcome.status()).isEqualTo(MonitorStatus.DEGRADED);
        assertThat(outcome.consecutiveFailures()).isEqualTo(1);
        assertThat(outcome.openIncident()).isFalse();
    }

    @Test
    void opensAnIncidentAfterThreeFailures() {
        var outcome = evaluate(MonitorStatus.DEGRADED, 2, 0, false);

        assertThat(outcome.status()).isEqualTo(MonitorStatus.DOWN);
        assertThat(outcome.openIncident()).isTrue();
    }

    @Test
    void doesNotOpenAnotherIncidentWhileDown() {
        var outcome = evaluate(MonitorStatus.DOWN, 3, 0, false);

        assertThat(outcome.status()).isEqualTo(MonitorStatus.DOWN);
        assertThat(outcome.openIncident()).isFalse();
    }

    @Test
    void entersRecoveryAfterOneSuccess() {
        var outcome = evaluate(MonitorStatus.DOWN, 3, 0, true);

        assertThat(outcome.status()).isEqualTo(MonitorStatus.RECOVERING);
        assertThat(outcome.resolveIncident()).isFalse();
    }

    @Test
    void resolvesAfterTwoSuccesses() {
        var outcome = evaluate(MonitorStatus.RECOVERING, 0, 1, true);

        assertThat(outcome.status()).isEqualTo(MonitorStatus.UP);
        assertThat(outcome.resolveIncident()).isTrue();
    }

    @Test
    void returnsToDownWhenRecoveryFails() {
        var outcome = evaluate(MonitorStatus.RECOVERING, 0, 1, false);

        assertThat(outcome.status()).isEqualTo(MonitorStatus.DOWN);
        assertThat(outcome.resolveIncident()).isFalse();
    }

    private IncidentStateMachine.Outcome evaluate(
            MonitorStatus status,
            int failures,
            int successes,
            boolean successful
    ) {
        return IncidentStateMachine.evaluate(
                new IncidentStateMachine.CurrentState(status, failures, successes),
                successful
        );
    }
}
