package com.pulseops.controlplane.organization;

import com.pulseops.controlplane.identity.CurrentUser;
import com.pulseops.controlplane.identity.IdentityService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TeamMembershipService {

    private final JdbcTemplate jdbcTemplate;
    private final IdentityService identities;
    private final ProjectAccessService access;

    public TeamMembershipService(
            JdbcTemplate jdbcTemplate,
            IdentityService identities,
            ProjectAccessService access
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.identities = identities;
        this.access = access;
    }

    @Transactional
    public List<TeamMemberResponse> findMembers(UUID teamId) {
        CurrentUser user = identities.currentUser();
        access.requireTeam(teamId, TeamRole.VIEWER);
        return jdbcTemplate.query("""
                SELECT u.id, u.subject, u.email, u.display_name, tm.role, u.id = ? AS current_user
                FROM team_memberships tm
                JOIN app_users u ON u.id = tm.user_id
                WHERE tm.team_id = ?
                ORDER BY CASE tm.role
                    WHEN 'OWNER' THEN 1 WHEN 'ADMIN' THEN 2 WHEN 'EDITOR' THEN 3 ELSE 4 END,
                    COALESCE(u.display_name, u.email, u.subject), u.id
                """, memberMapper(), user.id(), teamId);
    }

    @Transactional
    public TeamMemberResponse addMember(UUID teamId, AddTeamMemberRequest request) {
        CurrentUser actor = lockTeamAndRequireOwner(teamId);
        String subject = request.subject().trim();
        UUID targetId = findUserId(actor.issuer(), subject);
        Integer existing = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM team_memberships WHERE team_id = ? AND user_id = ?
                """, Integer.class, teamId, targetId);
        if (existing != null && existing > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a team member");
        }
        jdbcTemplate.update("""
                INSERT INTO team_memberships (team_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
                """, teamId, targetId, request.role().name());
        return findMember(teamId, targetId, actor.id());
    }

    @Transactional
    public TeamMemberResponse updateMember(UUID teamId, UUID userId, UpdateTeamMemberRequest request) {
        CurrentUser actor = lockTeamAndRequireOwner(teamId);
        TeamRole currentRole = findMemberRole(teamId, userId);
        if (currentRole == TeamRole.OWNER && request.role() != TeamRole.OWNER) {
            requireAnotherOwner(teamId);
        }
        jdbcTemplate.update("""
                UPDATE team_memberships SET role = ?, updated_at = now()
                WHERE team_id = ? AND user_id = ?
                """, request.role().name(), teamId, userId);
        return findMember(teamId, userId, actor.id());
    }

    @Transactional
    public void removeMember(UUID teamId, UUID userId) {
        lockTeamAndRequireOwner(teamId);
        TeamRole role = findMemberRole(teamId, userId);
        if (role == TeamRole.OWNER) {
            requireAnotherOwner(teamId);
        }
        jdbcTemplate.update("DELETE FROM team_memberships WHERE team_id = ? AND user_id = ?", teamId, userId);
    }

    private CurrentUser lockTeamAndRequireOwner(UUID teamId) {
        CurrentUser actor = identities.currentUser();
        List<UUID> teams = jdbcTemplate.query(
                "SELECT id FROM teams WHERE id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class), teamId);
        if (teams.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
        }
        List<TeamRole> roles = jdbcTemplate.query("""
                SELECT role FROM team_memberships WHERE team_id = ? AND user_id = ?
                """, (resultSet, rowNumber) -> TeamRole.valueOf(resultSet.getString("role")), teamId, actor.id());
        if (roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
        }
        if (roles.getFirst() != TeamRole.OWNER) {
            throw new AccessDeniedException("Only a team owner can manage memberships");
        }
        return actor;
    }

    private UUID findUserId(String issuer, String subject) {
        List<UUID> users = jdbcTemplate.query("""
                SELECT id FROM app_users WHERE issuer = ? AND subject = ?
                """, (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class), issuer, subject);
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "User not found; they must sign in to PulseOps before being added");
        }
        return users.getFirst();
    }

    private TeamRole findMemberRole(UUID teamId, UUID userId) {
        List<TeamRole> roles = jdbcTemplate.query("""
                SELECT role FROM team_memberships WHERE team_id = ? AND user_id = ?
                """, (resultSet, rowNumber) -> TeamRole.valueOf(resultSet.getString("role")), teamId, userId);
        if (roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team member not found");
        }
        return roles.getFirst();
    }

    private void requireAnotherOwner(UUID teamId) {
        Integer owners = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM team_memberships WHERE team_id = ? AND role = 'OWNER'
                """, Integer.class, teamId);
        if (owners == null || owners <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A team must retain at least one owner");
        }
    }

    private TeamMemberResponse findMember(UUID teamId, UUID userId, UUID actorId) {
        List<TeamMemberResponse> members = jdbcTemplate.query("""
                SELECT u.id, u.subject, u.email, u.display_name, tm.role, u.id = ? AS current_user
                FROM team_memberships tm
                JOIN app_users u ON u.id = tm.user_id
                WHERE tm.team_id = ? AND tm.user_id = ?
                """, memberMapper(), actorId, teamId, userId);
        if (members.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team member not found");
        }
        return members.getFirst();
    }

    private static RowMapper<TeamMemberResponse> memberMapper() {
        return (resultSet, rowNumber) -> new TeamMemberResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("subject"),
                resultSet.getString("email"),
                resultSet.getString("display_name"),
                TeamRole.valueOf(resultSet.getString("role")),
                resultSet.getBoolean("current_user")
        );
    }
}
