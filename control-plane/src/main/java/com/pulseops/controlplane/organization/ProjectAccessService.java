package com.pulseops.controlplane.organization;

import com.pulseops.controlplane.identity.CurrentUser;
import com.pulseops.controlplane.identity.IdentityService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectAccessService {

    private static final String PROJECT_ACCESS_SQL = """
            SELECT p.id, p.team_id, tm.role
            FROM projects p
            JOIN team_memberships tm ON tm.team_id = p.team_id
            WHERE p.id = ? AND tm.user_id = ?
            """;
    private static final String TEAM_ACCESS_SQL = """
            SELECT role FROM team_memberships WHERE team_id = ? AND user_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final IdentityService identities;

    public ProjectAccessService(JdbcTemplate jdbcTemplate, IdentityService identities) {
        this.jdbcTemplate = jdbcTemplate;
        this.identities = identities;
    }

    public ProjectAccess requireProject(UUID projectId, TeamRole required) {
        CurrentUser user = identities.currentUser();
        List<ProjectAccess> matches = jdbcTemplate.query(PROJECT_ACCESS_SQL, (resultSet, rowNumber) ->
                new ProjectAccess(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("team_id", UUID.class),
                        TeamRole.valueOf(resultSet.getString("role"))
                ), projectId, user.id());
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        ProjectAccess access = matches.getFirst();
        if (!access.role().allows(required)) {
            throw new AccessDeniedException("The project requires role " + required + " or higher");
        }
        return access;
    }

    public TeamRole requireTeam(UUID teamId, TeamRole required) {
        CurrentUser user = identities.currentUser();
        List<TeamRole> roles = jdbcTemplate.query(TEAM_ACCESS_SQL,
                (resultSet, rowNumber) -> TeamRole.valueOf(resultSet.getString("role")), teamId, user.id());
        if (roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
        }
        TeamRole role = roles.getFirst();
        if (!role.allows(required)) {
            throw new AccessDeniedException("The team requires role " + required + " or higher");
        }
        return role;
    }

    public record ProjectAccess(UUID projectId, UUID teamId, TeamRole role) {
    }
}
