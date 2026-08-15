package com.pulseops.controlplane.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddTeamMemberRequest(
        @NotBlank @Size(max = 512) String subject,
        @NotNull TeamRole role
) {
}
