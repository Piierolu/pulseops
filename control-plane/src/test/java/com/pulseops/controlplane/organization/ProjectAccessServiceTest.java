package com.pulseops.controlplane.organization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.pulseops.controlplane.identity.CurrentUser;
import com.pulseops.controlplane.identity.IdentityService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    private static final UUID USER_ID = UUID.fromString("08b71528-4f0b-4087-b870-d08eb9cc88d6");
    private static final UUID PROJECT_ID = UUID.fromString("828289fc-9bf1-4133-a822-d42942c0f38a");
    private static final UUID TEAM_ID = UUID.fromString("48a53e1c-796a-4799-909e-a6db26d9bd90");

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private IdentityService identities;

    private ProjectAccessService service;

    @BeforeEach
    void setUp() {
        service = new ProjectAccessService(jdbcTemplate, identities);
        when(identities.currentUser()).thenReturn(new CurrentUser(
                USER_ID, "https://identity.example.com", "user-1", null, null
        ));
    }

    @Test
    void deniesAViewerAnEditorOperation() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<
                        ProjectAccessService.ProjectAccess>>any(), eq(PROJECT_ID), eq(USER_ID)))
                .thenReturn(List.of(new ProjectAccessService.ProjectAccess(PROJECT_ID, TEAM_ID, TeamRole.VIEWER)));

        assertThatThrownBy(() -> service.requireProject(PROJECT_ID, TeamRole.EDITOR))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void hidesProjectsWithoutMembership() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<
                        ProjectAccessService.ProjectAccess>>any(), eq(PROJECT_ID), eq(USER_ID)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.requireProject(PROJECT_ID, TeamRole.VIEWER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                        ((ResponseStatusException) exception).getStatusCode().value()
                ).isEqualTo(404));
    }
}
