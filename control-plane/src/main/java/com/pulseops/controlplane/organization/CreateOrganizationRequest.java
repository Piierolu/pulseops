package com.pulseops.controlplane.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 80) String slug
) {
}
