package com.pulseops.controlplane.organization;

import java.util.UUID;

public record TeamResponse(UUID id, String name, String slug, TeamRole role) {
}
