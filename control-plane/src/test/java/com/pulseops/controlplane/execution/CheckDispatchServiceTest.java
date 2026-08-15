package com.pulseops.controlplane.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckDispatchServiceTest {

    @Test
    void createsStableExecutionIdentityForOneScheduleSlot() {
        UUID monitorId = UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a");
        Instant scheduledAt = Instant.parse("2026-08-14T12:00:00.123Z");

        UUID first = CheckDispatchService.executionId(monitorId, "local", scheduledAt);
        UUID recovered = CheckDispatchService.executionId(monitorId, "local", scheduledAt);
        UUID nextSlot = CheckDispatchService.executionId(monitorId, "local", scheduledAt.plusSeconds(15));

        assertThat(recovered).isEqualTo(first);
        assertThat(nextSlot).isNotEqualTo(first);
    }
}
