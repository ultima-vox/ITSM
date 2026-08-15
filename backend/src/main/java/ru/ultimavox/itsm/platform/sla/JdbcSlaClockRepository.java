package ru.ultimavox.itsm.platform.sla;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

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
                INSERT INTO sla_clock (id, org_id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, paused_at, state, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
                clock.id(),
                OrganizationContext.current(),
                clock.policyKey(),
                clock.aggregateId(),
                clock.metric(),
                Timestamp.from(clock.startedAt()),
                Timestamp.from(clock.dueAt()),
                clock.warningAt() == null ? null : Timestamp.from(clock.warningAt()),
                clock.pausedAt() == null ? null : Timestamp.from(clock.pausedAt()),
                clock.state().name()
        );
        return clock;
    }

    @Override
    public Optional<SlaClock> findById(UUID id) {
        List<SlaClock> rows = jdbc.query(
                """
                SELECT id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, paused_at, state
                FROM sla_clock WHERE id = ? AND org_id = ?
                """,
                (rs, i) -> map(rs),
                id, OrganizationContext.current()
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<SlaClock> findActiveByAggregate(UUID aggregateId) {
        return jdbc.query(
                """
                SELECT id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, paused_at, state
                FROM sla_clock
                WHERE org_id = ? AND aggregate_id = ? AND state IN ('RUNNING', 'PAUSED')
                """,
                (rs, i) -> map(rs),
                OrganizationContext.current(), aggregateId
        );
    }

    @Override
    public Optional<SlaClock> findActive(UUID aggregateId, String policyKey, String metric) {
        List<SlaClock> rows = jdbc.query(
                """
                SELECT id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, paused_at, state
                FROM sla_clock
                WHERE org_id = ? AND aggregate_id = ? AND policy_key = ? AND metric = ?
                  AND state IN ('RUNNING', 'PAUSED')
                ORDER BY started_at DESC
                LIMIT 1
                """,
                (rs, i) -> map(rs),
                OrganizationContext.current(), aggregateId, policyKey, metric
        );
        return rows.stream().findFirst();
    }

    @Override
    public SlaClock update(SlaClock clock) {
        jdbc.update(
                """
                UPDATE sla_clock
                SET due_at = ?, warning_at = ?, paused_at = ?, state = ?, updated_at = now()
                WHERE id = ? AND org_id = ?
                """,
                Timestamp.from(clock.dueAt()),
                clock.warningAt() == null ? null : Timestamp.from(clock.warningAt()),
                clock.pausedAt() == null ? null : Timestamp.from(clock.pausedAt()),
                clock.state().name(),
                clock.id(), OrganizationContext.current()
        );
        return clock;
    }

    @Override
    public void appendHistory(UUID clockId, String action, String actorId, String detailsJson) {
        jdbc.update(
                """
                INSERT INTO sla_clock_history (clock_id, action, actor_id, details)
                SELECT id, ?, ?, ?::jsonb FROM sla_clock WHERE id = ? AND org_id = ?
                """,
                action,
                actorId,
                detailsJson == null ? "{}" : detailsJson,
                clockId, OrganizationContext.current()
        );
    }

    @Override
    public List<SlaClock> findDueRunning(int limit) {
        return jdbc.query(
                """
                SELECT id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, paused_at, state
                FROM sla_clock
                WHERE org_id = ? AND state = 'RUNNING' AND due_at <= now()
                ORDER BY due_at
                LIMIT ?
                """,
                (rs, i) -> map(rs),
                OrganizationContext.current(), limit
        );
    }

    @Override
    public List<SlaClock> findDueForWarning(int limit) {
        return jdbc.query(
                """
                SELECT id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, paused_at, state
                FROM sla_clock
                WHERE org_id = ? AND state = 'RUNNING'
                  AND warning_at IS NOT NULL AND warned_at IS NULL
                  AND warning_at <= now() AND due_at > now()
                ORDER BY warning_at
                LIMIT ?
                """,
                (rs, i) -> map(rs),
                OrganizationContext.current(), limit
        );
    }

    @Override
    public void markWarned(UUID clockId) {
        jdbc.update(
                "UPDATE sla_clock SET warned_at = now(), updated_at = now() WHERE id = ? AND org_id = ? AND warned_at IS NULL",
                clockId, OrganizationContext.current()
        );
    }

    @Override
    public List<String> distinctOrgIdsWithDueOrWarnClocks() {
        return jdbc.query(
                """
                SELECT DISTINCT org_id FROM sla_clock
                WHERE state = 'RUNNING'
                  AND (due_at <= now()
                       OR (warning_at IS NOT NULL AND warned_at IS NULL
                           AND warning_at <= now() AND due_at > now()))
                """,
                (rs, i) -> rs.getString(1)
        );
    }

    @Override
    public int achieveFor(UUID aggregateId, String actorId) {
        List<SlaClock> clocks = findActiveByAggregate(aggregateId);
        for (SlaClock clock : clocks) {
            jdbc.update(
                    "UPDATE sla_clock SET state = 'ACHIEVED', paused_at = NULL, updated_at = now() WHERE id = ? AND org_id = ?",
                    clock.id(), OrganizationContext.current()
            );
            appendHistory(clock.id(), "ACHIEVED", actorId, "{}");
        }
        return clocks.size();
    }

    @Override
    public Optional<SlaClock> findActive(UUID aggregateId, String metric) {
        List<SlaClock> rows = jdbc.query(
                """
                SELECT id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, paused_at, state
                FROM sla_clock
                WHERE org_id = ? AND aggregate_id = ? AND metric = ?
                  AND state IN ('RUNNING', 'PAUSED')
                ORDER BY started_at DESC
                LIMIT 1
                """,
                (rs, i) -> map(rs),
                OrganizationContext.current(), aggregateId, metric
        );
        return rows.stream().findFirst();
    }

    private SlaClock map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp warning = rs.getTimestamp("warning_at");
        Timestamp paused = rs.getTimestamp("paused_at");
        return new SlaClock(
                rs.getObject("id", UUID.class),
                rs.getString("policy_key"),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("metric"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("due_at").toInstant(),
                warning == null ? null : warning.toInstant(),
                paused == null ? null : paused.toInstant(),
                SlaClock.State.valueOf(rs.getString("state"))
        );
    }
}
