package com.pulseops.controlplane.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("pulseops-api");

    @Test
    void acceptsTheRequiredAudience() {
        assertThat(validator.validate(jwt(List.of("other", "pulseops-api"))).hasErrors()).isFalse();
    }

    @Test
    void rejectsATokenWithoutTheRequiredAudience() {
        assertThat(validator.validate(jwt(List.of("other"))).hasErrors()).isTrue();
    }

    private Jwt jwt(List<String> audiences) {
        Instant now = Instant.parse("2026-08-14T12:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("https://identity.example.com")
                .subject("user-1")
                .audience(audiences)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
