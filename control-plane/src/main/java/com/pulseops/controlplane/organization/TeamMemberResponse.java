package com.pulseops.controlplane.organization;

import java.util.UUID;

public record TeamMemberResponse(
        UUID id,
        String subject,
        String email,
        String displayName,
        TeamRole role,
        boolean currentUser
) {
}
