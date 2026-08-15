package com.pulseops.controlplane.execution;

import java.time.Duration;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class CommandOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    CommandOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void enqueue(CheckCommand command, String topic, String payload, TraceHeaders traceHeaders) {
        jdbcTemplate.update("""
                INSERT INTO command_outbox (
                    execution_id, monitor_id, location, scheduled_at,
                    destination_topic, message_key, payload, traceparent, tracestate
                )
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                command.executionId(),
                command.monitorId(),
                command.location(),
                Timestamp.from(command.scheduledAt()),
                topic,
                command.executionId().toString(),
                payload,
                traceHeaders.traceparent(),
                traceHeaders.tracestate()
        );
    }

    @Transactional
    List<PendingCommand> claimBatch(int batchSize, Duration claimLease) {
        UUID claimToken = UUID.randomUUID();
        return jdbcTemplate.query("""
                WITH candidates AS (
                    SELECT execution_id
                    FROM command_outbox
                    WHERE published_at IS NULL
                      AND available_at <= now()
                      AND (claimed_until IS NULL OR claimed_until < now())
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE command_outbox AS outbox
                SET claim_token = ?,
                    claimed_until = now() + (? * interval '1 millisecond'),
                    attempt_count = outbox.attempt_count + 1
                FROM candidates
                WHERE outbox.execution_id = candidates.execution_id
                RETURNING outbox.execution_id,
                          outbox.destination_topic,
                          outbox.message_key,
                          outbox.payload::text,
                          outbox.traceparent,
                          outbox.tracestate,
                          outbox.claim_token,
                          outbox.attempt_count
                """,
                (resultSet, rowNumber) -> new PendingCommand(
                        resultSet.getObject("execution_id", UUID.class),
                        resultSet.getString("destination_topic"),
                        resultSet.getString("message_key"),
                        resultSet.getString("payload"),
                        new TraceHeaders(
                                resultSet.getString("traceparent"),
                                resultSet.getString("tracestate")
                        ),
                        resultSet.getObject("claim_token", UUID.class),
                        resultSet.getInt("attempt_count")
                ),
                batchSize,
                claimToken,
                claimLease.toMillis()
        );
    }

    boolean markPublished(PendingCommand command) {
        return jdbcTemplate.update("""
                UPDATE command_outbox
                SET published_at = now(), claim_token = NULL, claimed_until = NULL, last_error = NULL
                WHERE execution_id = ? AND claim_token = ?
                """, command.executionId(), command.claimToken()) == 1;
    }

    boolean markFailed(PendingCommand command, Duration retryDelay, String error) {
        return jdbcTemplate.update("""
                UPDATE command_outbox
                SET available_at = now() + (? * interval '1 millisecond'),
                    claim_token = NULL,
                    claimed_until = NULL,
                    last_error = ?
                WHERE execution_id = ? AND claim_token = ?
                """,
                retryDelay.toMillis(),
                bounded(error),
                command.executionId(),
                command.claimToken()
        ) == 1;
    }

    double pendingCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM command_outbox WHERE published_at IS NULL",
                Long.class
        );
        return count == null ? 0 : count.doubleValue();
    }

    double oldestPendingAgeSeconds() {
        Double age = jdbcTemplate.queryForObject("""
                SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0)
                FROM command_outbox
                WHERE published_at IS NULL
                """, Double.class);
        return age == null ? 0 : Math.max(0, age);
    }

    private String bounded(String error) {
        if (error == null) {
            return "Unknown publication error";
        }
        return error.length() <= 2048 ? error : error.substring(0, 2048);
    }
}
