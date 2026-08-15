package com.pulseops.controlplane.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "check_results")
@IdClass(CheckResultId.class)
class CheckResult {

    @Id
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "monitor_id", nullable = false)
    private UUID monitorId;

    @Column(name = "agent_id", nullable = false, length = 120)
    private String agentId;

    @Column(nullable = false, length = 80)
    private String location;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(length = 2048)
    private String error;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Id
    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected CheckResult() {
    }

    static CheckResult from(CheckResultMessage message) {
        CheckResult result = new CheckResult();
        result.id = UUID.randomUUID();
        result.executionId = message.executionId();
        result.monitorId = message.monitorId();
        result.agentId = message.agentId();
        result.location = message.location();
        result.status = message.status();
        result.latencyMs = message.latencyMs();
        result.statusCode = message.statusCode();
        result.error = message.error();
        result.details = message.details() == null ? Map.of() : message.details();
        result.checkedAt = message.checkedAt();
        return result;
    }

    CheckResultResponse toResponse() {
        return new CheckResultResponse(
                id, executionId, monitorId, agentId, location, status,
                latencyMs, statusCode, error, details, checkedAt
        );
    }
}
