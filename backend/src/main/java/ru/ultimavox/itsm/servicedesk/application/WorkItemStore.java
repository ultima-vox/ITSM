package ru.ultimavox.itsm.servicedesk.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;
import ru.ultimavox.itsm.servicedesk.domain.WorkItemComment;

/** JDBC persistence for Service Desk aggregates. */
@Repository
class WorkItemStore {

  private final JdbcTemplate jdbc;

  WorkItemStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  void insert(WorkItem item) {
    jdbc.update(
        """
        INSERT INTO work_item (
          id, number, type, title, description, service, state, priority,
          impact, urgency, assignee_id, requester_id, team_id,
          resolution_code, resolution_notes, created_at, updated_at, closed_at
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        item.id(),
        item.number(),
        item.type().name(),
        item.title(),
        item.description(),
        item.service(),
        item.state().name(),
        item.priority().name(),
        item.impact().name(),
        item.urgency().name(),
        item.assigneeId(),
        item.requesterId(),
        item.teamId(),
        item.resolutionCode(),
        item.resolutionNotes(),
        Timestamp.from(item.createdAt()),
        Timestamp.from(item.updatedAt()),
        item.closedAt() == null ? null : Timestamp.from(item.closedAt())
    );
  }

  void update(WorkItem item) {
    int updated = jdbc.update(
        """
        UPDATE work_item SET
          title = ?, description = ?, service = ?, state = ?, priority = ?,
          impact = ?, urgency = ?, assignee_id = ?, team_id = ?,
          resolution_code = ?, resolution_notes = ?, updated_at = ?, closed_at = ?
        WHERE id = ?
        """,
        item.title(),
        item.description(),
        item.service(),
        item.state().name(),
        item.priority().name(),
        item.impact().name(),
        item.urgency().name(),
        item.assigneeId(),
        item.teamId(),
        item.resolutionCode(),
        item.resolutionNotes(),
        Timestamp.from(item.updatedAt()),
        item.closedAt() == null ? null : Timestamp.from(item.closedAt()),
        item.id()
    );
    if (updated == 0) {
      throw new WorkItemNotFoundException(item.id());
    }
  }

  Optional<WorkItem> findById(UUID id) {
    List<WorkItem> rows = jdbc.query(
        """
        SELECT id, number, type, title, description, service, state, priority,
               impact, urgency, assignee_id, requester_id, team_id,
               resolution_code, resolution_notes, created_at, updated_at, closed_at
        FROM work_item WHERE id = ?
        """,
        (rs, rowNum) -> mapWorkItem(rs),
        id
    );
    return rows.stream().findFirst();
  }

  WorkItem requireById(UUID id) {
    return findById(id).orElseThrow(() -> new WorkItemNotFoundException(id));
  }

  long count(WorkItemQuery.Filter filter) {
    SqlAndArgs built = buildWhere(filter);
    Long total = jdbc.queryForObject(
        "SELECT count(*) FROM work_item" + built.sql(),
        Long.class,
        built.args().toArray()
    );
    return total == null ? 0L : total;
  }

  List<WorkItem> search(WorkItemQuery.Filter filter, int page, int size) {
    SqlAndArgs built = buildWhere(filter);
    int offset = Math.max(page, 0) * Math.max(size, 1);
    List<Object> args = new ArrayList<>(built.args());
    args.add(size);
    args.add(offset);
    return jdbc.query(
        """
        SELECT id, number, type, title, description, service, state, priority,
               impact, urgency, assignee_id, requester_id, team_id,
               resolution_code, resolution_notes, created_at, updated_at, closed_at
        FROM work_item
        """
            + built.sql()
            + " ORDER BY updated_at DESC LIMIT ? OFFSET ?",
        (rs, rowNum) -> mapWorkItem(rs),
        args.toArray()
    );
  }

  void insertComment(WorkItemComment comment) {
    jdbc.update(
        """
        INSERT INTO work_item_comment (id, work_item_id, author_id, body, created_at)
        VALUES (?,?,?,?,?)
        """,
        comment.id(),
        comment.workItemId(),
        comment.authorId(),
        comment.body(),
        Timestamp.from(comment.createdAt())
    );
  }

  List<WorkItemComment> listComments(UUID workItemId) {
    return jdbc.query(
        """
        SELECT id, work_item_id, author_id, body, created_at
        FROM work_item_comment
        WHERE work_item_id = ?
        ORDER BY created_at ASC
        """,
        (rs, rowNum) -> new WorkItemComment(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("work_item_id"),
            rs.getString("author_id"),
            rs.getString("body"),
            rs.getTimestamp("created_at").toInstant()
        ),
        workItemId
    );
  }

  long countOpen() {
    Long n = jdbc.queryForObject(
        """
        SELECT count(*) FROM work_item
        WHERE state NOT IN ('CLOSED', 'CANCELLED')
        """,
        Long.class
    );
    return n == null ? 0L : n;
  }

  long countSlaDueToday() {
    try {
      Long n = jdbc.queryForObject(
          """
          SELECT count(DISTINCT sc.aggregate_id)
          FROM sla_clock sc
          JOIN work_item wi ON wi.id = sc.aggregate_id
          WHERE sc.state = 'RUNNING'
            AND sc.due_at::date = CURRENT_DATE
            AND wi.state NOT IN ('CLOSED', 'CANCELLED')
          """,
          Long.class
      );
      return n == null ? 0L : n;
    } catch (Exception ignored) {
      return 0L;
    }
  }

  long countSlaBreached() {
    try {
      Long n = jdbc.queryForObject(
          """
          SELECT count(DISTINCT sc.aggregate_id)
          FROM sla_clock sc
          JOIN work_item wi ON wi.id = sc.aggregate_id
          WHERE sc.state = 'BREACHED'
            AND wi.state NOT IN ('CLOSED', 'CANCELLED')
          """,
          Long.class
      );
      return n == null ? 0L : n;
    } catch (Exception ignored) {
      return 0L;
    }
  }

  void startResponseSla(UUID workItemId, Instant startedAt, Instant dueAt, Instant warningAt) {
    try {
      jdbc.update(
          """
          INSERT INTO sla_clock (id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, state, updated_at)
          VALUES (?,?,?,?,?,?,?,?,?)
          """,
          UUID.randomUUID(),
          "work-item.response.default",
          workItemId,
          "response",
          Timestamp.from(startedAt),
          Timestamp.from(dueAt),
          warningAt == null ? null : Timestamp.from(warningAt),
          "RUNNING",
          Timestamp.from(startedAt)
      );
    } catch (Exception ignored) {
      // SLA tables or calculator unavailable — creation must not fail.
    }
  }

  private static WorkItem mapWorkItem(ResultSet rs) throws SQLException {
    Instant closedAt = rs.getTimestamp("closed_at") == null
        ? null
        : rs.getTimestamp("closed_at").toInstant();
    return new WorkItem(
        (UUID) rs.getObject("id"),
        rs.getString("number"),
        Type.valueOf(rs.getString("type")),
        rs.getString("title"),
        rs.getString("description"),
        rs.getString("service"),
        State.valueOf(rs.getString("state")),
        Priority.valueOf(rs.getString("priority")),
        Impact.valueOf(rs.getString("impact")),
        Urgency.valueOf(rs.getString("urgency")),
        rs.getString("assignee_id"),
        rs.getString("requester_id"),
        rs.getString("team_id"),
        rs.getString("resolution_code"),
        rs.getString("resolution_notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        closedAt
    );
  }

  private static SqlAndArgs buildWhere(WorkItemQuery.Filter filter) {
    StringBuilder sql = new StringBuilder(" WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (filter.state() != null) {
      sql.append(" AND state = ?");
      args.add(filter.state().name());
    }
    if (filter.type() != null) {
      sql.append(" AND type = ?");
      args.add(filter.type().name());
    }
    if (filter.assigneeId() != null && !filter.assigneeId().isBlank()) {
      sql.append(" AND assignee_id = ?");
      args.add(filter.assigneeId());
    }
    if (filter.priority() != null) {
      sql.append(" AND priority = ?");
      args.add(filter.priority().name());
    }
    if (filter.query() != null && !filter.query().isBlank()) {
      sql.append(" AND (lower(title) LIKE ? OR lower(description) LIKE ? OR lower(number) LIKE ?)");
      String pattern = "%" + filter.query().trim().toLowerCase() + "%";
      args.add(pattern);
      args.add(pattern);
      args.add(pattern);
    }
    return new SqlAndArgs(sql.toString(), args);
  }

  private record SqlAndArgs(String sql, List<Object> args) {}
}
