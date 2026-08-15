package com.pulseops.controlplane.organization;

public enum TeamRole {
    VIEWER,
    EDITOR,
    ADMIN,
    OWNER;

    public boolean allows(TeamRole required) {
        return ordinal() >= required.ordinal();
    }
}
