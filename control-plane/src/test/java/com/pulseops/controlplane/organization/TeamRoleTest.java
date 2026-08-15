package com.pulseops.controlplane.organization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeamRoleTest {

    @Test
    void ordersAccessFromOwnerToViewer() {
        assertThat(TeamRole.OWNER.allows(TeamRole.ADMIN)).isTrue();
        assertThat(TeamRole.ADMIN.allows(TeamRole.ADMIN)).isTrue();
        assertThat(TeamRole.ADMIN.allows(TeamRole.EDITOR)).isTrue();
        assertThat(TeamRole.EDITOR.allows(TeamRole.VIEWER)).isTrue();
        assertThat(TeamRole.EDITOR.allows(TeamRole.ADMIN)).isFalse();
        assertThat(TeamRole.VIEWER.allows(TeamRole.EDITOR)).isFalse();
    }
}
