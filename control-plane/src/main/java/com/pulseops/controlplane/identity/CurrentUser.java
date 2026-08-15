package com.pulseops.controlplane.identity;

import java.util.UUID;

public record CurrentUser(UUID id, String issuer, String subject, String email, String name) {
}
