package com.pulseops.controlplane.execution;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class CheckResultId implements Serializable {

    private UUID id;
    private Instant checkedAt;

    public CheckResultId() {
    }

    public CheckResultId(UUID id, Instant checkedAt) {
        this.id = id;
        this.checkedAt = checkedAt;
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof CheckResultId other)) {
            return false;
        }
        return Objects.equals(id, other.id) && Objects.equals(checkedAt, other.checkedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, checkedAt);
    }
}
