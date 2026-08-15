package com.pulseops.controlplane.organization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulseops.controlplane.identity.CurrentUser;
import com.pulseops.controlplane.identity.IdentityService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TeamMembershipServiceTest {

    private static final UUID TEAM_ID = UUID.fromString("0f120e27-151c-4cb8-a927-a3756fdd407e");
    private static final UUID ACTOR_ID = UUID.fromString("b0f20b09-e1c3-4591-bd97-bc21832b6075");
    private static final UUID MEMBER_ID = UUID.fromString("c11f948c-7ec6-4cab-8cf8-9387b640fe22");

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private IdentityService identities;

    @Mock
    private ProjectAccessService access;

    private TeamMembershipService service;

    @BeforeEach
    void setUp() {
        service = new TeamMembershipService(jdbcTemplate, identities, access);
        when(identities.currentUser()).thenReturn(new CurrentUser(
                ACTOR_ID, "https://identity.example.com", "owner-subject", "owner@example.com", "Owner"
        ));
    }

    @Test
    void allowsAnyMemberToListTheTeamDirectory() {
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<TeamMemberResponse>>any(),
                eq(ACTOR_ID), eq(TEAM_ID))).thenReturn(List.of());

        service.findMembers(TEAM_ID);

        verify(access).requireTeam(TEAM_ID, TeamRole.VIEWER);
    }

    @Test
    void deniesMembershipWritesToNonOwners() {
        stubTeamLock();
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<TeamRole>>any(),
                eq(TEAM_ID), eq(ACTOR_ID))).thenReturn(List.of(TeamRole.ADMIN));

        assertThatThrownBy(() -> service.removeMember(TEAM_ID, MEMBER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void refusesToDemoteTheLastOwner() {
        stubOwnerLock();
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<TeamRole>>any(),
                eq(TEAM_ID), eq(MEMBER_ID))).thenReturn(List.of(TeamRole.OWNER));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TEAM_ID))).thenReturn(1);

        assertThatThrownBy(() -> service.updateMember(
                TEAM_ID, MEMBER_ID, new UpdateTeamMemberRequest(TeamRole.ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                        ((ResponseStatusException) exception).getStatusCode().value()).isEqualTo(409));
        verify(jdbcTemplate, never()).update(anyString(), eq(TeamRole.ADMIN.name()), eq(TEAM_ID), eq(MEMBER_ID));
    }

    @Test
    void removesAnOwnerWhenAnotherOwnerRemains() {
        stubOwnerLock();
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<TeamRole>>any(),
                eq(TEAM_ID), eq(MEMBER_ID))).thenReturn(List.of(TeamRole.OWNER));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TEAM_ID))).thenReturn(2);

        service.removeMember(TEAM_ID, MEMBER_ID);

        verify(jdbcTemplate).update(anyString(), eq(TEAM_ID), eq(MEMBER_ID));
    }

    private void stubTeamLock() {
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<UUID>>any(), eq(TEAM_ID)))
                .thenReturn(List.of(TEAM_ID));
    }

    private void stubOwnerLock() {
        stubTeamLock();
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<TeamRole>>any(),
                eq(TEAM_ID), eq(ACTOR_ID))).thenReturn(List.of(TeamRole.OWNER));
    }
}
