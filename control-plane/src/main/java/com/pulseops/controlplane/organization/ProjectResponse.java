package com.pulseops.controlplane.organization;

import java.util.UUID;

public record ProjectResponse(UUID id, UUID teamId, String name, String slug, TeamRole role) {
}
