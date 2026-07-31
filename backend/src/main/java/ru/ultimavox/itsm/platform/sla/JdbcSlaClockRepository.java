package ru.ultimavox.itsm.platform.sla;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcSlaClockRepository implements SlaClockRepository {

    private final JdbcTemplate jdbc;

    JdbcSlaClockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SlaClock insert(SlaClock clock) {
        jdbc.update(
                """
                INSERT INTO sla_clock (id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, state, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
                clock.id(),
                clock.policyKey(),
                clock.aggregateId(),
                clock.metric(),
                Timestamp.from(clock.startedAt()),
                Timestamp.from(clock.dueAt()),
                clock.warningAt() == null ? null : Timestamp.from(clock.warningAt()),
                clock.state().name()
        );
        return clock;
    }

    @Override
    public Optional<SlaClock> findById(UUID id) {
        List<SlaClock> rows = jdbc.query(
                """
                SELECT id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, state
                FROM sla_clock WHERE id = ?
                """,
                (rs, i) -> map(rs),
                id
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<SlaClock> findActiveByAggregate(UUID aggregateId) {
        return jdbc.query(
                """
                SELECT id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, state
                FROM sla_clock
                WHERE aggregate_id = ? AND state IN ('RUNNING', 'PAUSED')
                """,
                (rs, i) -> map(rs),
                aggregateId
        );
    }

    @Override
    public Optional<SlaClock> findActive(UUID aggregateId, String policyKey, String metric) {
        List<SlaClock> rows = jdbc.query(
                """
                SELECT id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, state
                FROM sla_clock
                WHERE aggregate_id = ? AND policy_key = ? AND metric = ?
                  AND state IN ('RUNNING', 'PAUSED')
                ORDER BY started_at DESC
                LIMIT 1
                """,
                (rs, i) -> map(rs),
                aggregateId, policyKey, metric
        );
        return rows.stream().findFirst();
    }

    @Override
    public SlaClock update(SlaClock clock) {
        jdbc.update(
                """
                UPDATE sla_clock
                SET due_at = ?, warning_at = ?, state = ?, updated_at = now()
                WHERE id = ?
                """,
                Timestamp.from(clock.dueAt()),
                clock.warningAt() == null ? null : Timestamp.from(clock.warningAt()),
                clock.state().name(),
                clock.id()
        );
        return clock;
    }

    @Override
    public void appendHistory(UUID clockId, String action, String actorId, String detailsJson) {
        jdbc.update(
                """
                INSERT INTO sla_clock_history (clock_id, action, actor_id, details)
                VALUES (?, ?, ?, ?::jsonb)
                """,
                clockId,
                action,
                actorId,
                detailsJson == null ? "{}" : detailsJson
        );
    }

    @Override
    public List<SlaClock> findDueRunning(int limit) {
        return jdbc.query(
                """
                SELECT id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, state
                FROM sla_clock
                WHERE state = 'RUNNING' AND due_at <= now()
                ORDER BY due_at
                LIMIT ?
                """,
                (rs, i) -> map(rs),
                limit
        );
    }

    private SlaClock map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp warning = rs.getTimestamp("warning_at");
        return new SlaClock(
                rs.getObject("id", UUID.class),
                rs.getString("policy_key"),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("metric"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("due_at").toInstant(),
                warning == null ? null : warning.toInstant(),
                SlaClock.State.valueOf(rs.getString("state"))
        );
    }
}
