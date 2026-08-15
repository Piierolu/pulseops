package com.pulseops.controlplane.organization;

import com.pulseops.controlplane.identity.CurrentUser;
import com.pulseops.controlplane.identity.IdentityService;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationService {

    private static final Pattern VALID_SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final JdbcTemplate jdbcTemplate;
    private final IdentityService identities;
    private final ProjectAccessService access;

    public OrganizationService(JdbcTemplate jdbcTemplate, IdentityService identities, ProjectAccessService access) {
        this.jdbcTemplate = jdbcTemplate;
        this.identities = identities;
        this.access = access;
    }

    @Transactional
    public List<TeamResponse> findTeams() {
        CurrentUser user = identities.currentUser();
        return jdbcTemplate.query("""
                SELECT t.id, t.name, t.slug, tm.role
                FROM teams t JOIN team_memberships tm ON tm.team_id = t.id
                WHERE tm.user_id = ? ORDER BY t.name, t.id
                """, (resultSet, rowNumber) -> new TeamResponse(
                resultSet.getObject("id", UUID.class), resultSet.getString("name"),
                resultSet.getString("slug"), TeamRole.valueOf(resultSet.getString("role"))
        ), user.id());
    }

    @Transactional
    public TeamResponse createTeam(CreateOrganizationRequest request) {
        CurrentUser user = identities.currentUser();
        UUID id = UUID.randomUUID();
        String slug = uniqueSlug(request.name(), request.slug(), null);
        jdbcTemplate.update("""
                INSERT INTO teams (id, name, slug, created_at, updated_at) VALUES (?, ?, ?, now(), now())
                """, id, request.name().trim(), slug);
        jdbcTemplate.update("""
                INSERT INTO team_memberships (team_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, 'OWNER', now(), now())
                """, id, user.id());
        return new TeamResponse(id, request.name().trim(), slug, TeamRole.OWNER);
    }

    @Transactional
    public List<ProjectResponse> findAccessibleProjects() {
        CurrentUser user = identities.currentUser();
        return jdbcTemplate.query("""
                SELECT p.id, p.team_id, p.name, p.slug, tm.role
                FROM projects p JOIN team_memberships tm ON tm.team_id = p.team_id
                WHERE tm.user_id = ? ORDER BY p.name, p.id
                """, projectMapper(), user.id());
    }

    @Transactional
    public List<ProjectResponse> findTeamProjects(UUID teamId) {
        TeamRole role = access.requireTeam(teamId, TeamRole.VIEWER);
        return jdbcTemplate.query("""
                SELECT p.id, p.team_id, p.name, p.slug, ? AS role
                FROM projects p WHERE p.team_id = ? ORDER BY p.name, p.id
                """, projectMapper(), role.name(), teamId);
    }

    @Transactional
    public ProjectResponse createProject(UUID teamId, CreateOrganizationRequest request) {
        TeamRole role = access.requireTeam(teamId, TeamRole.ADMIN);
        UUID id = UUID.randomUUID();
        String slug = uniqueSlug(request.name(), request.slug(), teamId);
        jdbcTemplate.update("""
                INSERT INTO projects (id, team_id, name, slug, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                """, id, teamId, request.name().trim(), slug);
        return new ProjectResponse(id, teamId, request.name().trim(), slug, role);
    }

    @Transactional
    public void grantLegacyOwner(CurrentUser user) {
        jdbcTemplate.update("""
                INSERT INTO team_memberships (team_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, 'OWNER', now(), now())
                ON CONFLICT (team_id, user_id) DO UPDATE SET role = 'OWNER', updated_at = now()
                """, OrganizationConstants.LEGACY_TEAM_ID, user.id());
    }

    private String uniqueSlug(String name, String requested, UUID teamId) {
        String base = requested == null || requested.isBlank() ? generateSlug(name) : requested.trim();
        if (!VALID_SLUG.matcher(base).matches() || base.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Slug must contain lowercase letters, numbers, and single hyphens");
        }
        if (requested != null && !requested.isBlank()) {
            if (slugExists(base, teamId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already exists");
            }
            return base;
        }
        String candidate = base;
        for (int suffix = 2; slugExists(candidate, teamId); suffix++) {
            String ending = "-" + suffix;
            candidate = base.substring(0, Math.min(base.length(), 80 - ending.length())) + ending;
        }
        return candidate;
    }

    private boolean slugExists(String slug, UUID teamId) {
        Integer count = teamId == null
                ? jdbcTemplate.queryForObject("SELECT count(*) FROM teams WHERE slug = ?", Integer.class, slug)
                : jdbcTemplate.queryForObject("SELECT count(*) FROM projects WHERE team_id = ? AND slug = ?",
                        Integer.class, teamId, slug);
        return count != null && count > 0;
    }

    private static String generateSlug(String name) {
        String slug = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name cannot generate a valid slug");
        }
        return slug.substring(0, Math.min(slug.length(), 80));
    }

    private static org.springframework.jdbc.core.RowMapper<ProjectResponse> projectMapper() {
        return (resultSet, rowNumber) -> new ProjectResponse(
                resultSet.getObject("id", UUID.class), resultSet.getObject("team_id", UUID.class),
                resultSet.getString("name"), resultSet.getString("slug"),
                TeamRole.valueOf(resultSet.getString("role"))
        );
    }
}
