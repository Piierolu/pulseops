package com.pulseops.controlplane.identity;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

    private static final String UPSERT_SQL = """
            INSERT INTO app_users (id, issuer, subject, email, display_name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (issuer, subject) DO UPDATE SET
                email = COALESCE(EXCLUDED.email, app_users.email),
                display_name = COALESCE(EXCLUDED.display_name, app_users.display_name),
                updated_at = now()
            RETURNING id, issuer, subject, email, display_name
            """;

    private final JdbcTemplate jdbcTemplate;

    public IdentityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public CurrentUser currentUser() {
        return resolve(SecurityContextHolder.getContext().getAuthentication());
    }

    @Transactional
    public CurrentUser resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("An authenticated principal is required");
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
            String subject = jwt.getSubject();
            if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
                throw new IllegalStateException("OIDC token must contain issuer and subject");
            }
            return upsert(issuer, subject, optionalClaim(jwt.getClaims(), "email"),
                    optionalClaim(jwt.getClaims(), "name"));
        }
        if (authentication.getPrincipal() instanceof DemoPrincipal demo) {
            return upsert(demo.issuer(), demo.subject(), null, "Demo User");
        }
        throw new IllegalStateException("Unsupported authenticated principal");
    }

    public CurrentUser upsert(String issuer, String subject, String email, String name) {
        return jdbcTemplate.queryForObject(UPSERT_SQL, (resultSet, rowNumber) -> new CurrentUser(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("issuer"),
                resultSet.getString("subject"),
                resultSet.getString("email"),
                resultSet.getString("display_name")
        ), UUID.randomUUID(), issuer, subject, email, name);
    }

    private static String optionalClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
