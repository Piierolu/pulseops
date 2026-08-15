package com.pulseops.controlplane.execution;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ExecutionReceiptRepository {

    private final JdbcTemplate jdbcTemplate;

    ExecutionReceiptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean record(CheckResultMessage message) {
        return jdbcTemplate.update("""
                INSERT INTO check_execution_receipts (execution_id, monitor_id, checked_at)
                VALUES (?, ?, ?)
                ON CONFLICT (execution_id) DO NOTHING
                """,
                message.executionId(),
                message.monitorId(),
                Timestamp.from(message.checkedAt())
        ) == 1;
    }
}
