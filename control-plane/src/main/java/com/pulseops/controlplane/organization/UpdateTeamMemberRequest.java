package com.pulseops.controlplane.organization;

import jakarta.validation.constraints.NotNull;

public record UpdateTeamMemberRequest(@NotNull TeamRole role) {
}
