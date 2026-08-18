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
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;
import ru.ultimavox.itsm.servicedesk.domain.WorkItemComment;

/** JDBC persistence for Service Desk aggregates. */
@Repository
public class WorkItemStore {

  private final JdbcTemplate jdbc;

  public WorkItemStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  void insert(WorkItem item) {
    jdbc.update(
        """
        INSERT INTO work_item (
          id, org_id, number, type, title, description, service, state, priority,
          impact, urgency, assignee_id, requester_id, team_id,
          resolution_code, resolution_notes, escalated, created_at, updated_at, closed_at, version
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        item.id(),
        OrganizationContext.current(),
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
        item.escalated(),
        Timestamp.from(item.createdAt()),
        Timestamp.from(item.updatedAt()),
        item.closedAt() == null ? null : Timestamp.from(item.closedAt()),
        item.version()
    );
  }

  void update(WorkItem item) {
    int updated = jdbc.update(
        """
        UPDATE work_item SET
          title = ?, description = ?, service = ?, state = ?, priority = ?,
          impact = ?, urgency = ?, assignee_id = ?, team_id = ?,
          resolution_code = ?, resolution_notes = ?, escalated = ?,
          updated_at = ?, closed_at = ?, version = version + 1
        WHERE id = ? AND org_id = ? AND version = ?
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
        item.escalated(),
        Timestamp.from(item.updatedAt()),
        item.closedAt() == null ? null : Timestamp.from(item.closedAt()),
        item.id(),
        OrganizationContext.current(),
        item.version()
    );
    if (updated == 0) {
      throw new WorkItemConcurrencyException(item.id(), item.version());
    }
  }

  Optional<WorkItem> findById(UUID id) {
    List<WorkItem> rows = jdbc.query(
        """
        SELECT id, number, type, title, description, service, state, priority,
               impact, urgency, assignee_id, requester_id, team_id,
               resolution_code, resolution_notes, escalated, created_at, updated_at, closed_at, version
        FROM work_item WHERE id = ? AND org_id = ?
        """,
        (rs, rowNum) -> mapWorkItem(rs),
        id,
        OrganizationContext.current()
    );
    return rows.stream().findFirst();
  }

  WorkItem requireById(UUID id) {
    return findById(id).orElseThrow(() -> new WorkItemNotFoundException(id));
  }

  long count(WorkItemQuery.Filter filter) {
    SqlAndArgs built = buildWhere(filter, OrganizationContext.current());
    Long total = jdbc.queryForObject(
        "SELECT count(*) FROM work_item" + built.sql(),
        Long.class,
        built.args().toArray()
    );
    return total == null ? 0L : total;
  }

  Double averageCsatSince(Instant since) {
    Double average = jdbc.queryForObject(
        "SELECT avg(rating)::double precision * 20 FROM work_item_survey WHERE org_id = ? AND submitted_at >= ?",
        Double.class, OrganizationContext.current(), Timestamp.from(since));
    return average == null ? null : Math.round(average * 10.0) / 10.0;
  }

  List<WorkItem> search(WorkItemQuery.Filter filter, int page, int size) {
    SqlAndArgs built = buildWhere(filter, OrganizationContext.current());
    int offset = Math.max(page, 0) * Math.max(size, 1);
    List<Object> args = new ArrayList<>(built.args());
    args.add(size);
    args.add(offset);
    WorkItemQuery.SortBy sort = filter.sort() != null ? filter.sort() : new WorkItemQuery.SortBy("updated_at", true);
    return jdbc.query(
        """
        SELECT id, number, type, title, description, service, state, priority,
               impact, urgency, assignee_id, requester_id, team_id,
               resolution_code, resolution_notes, escalated, created_at, updated_at, closed_at, version
        FROM work_item
        """
            + built.sql()
            + " ORDER BY " + sort.field() + (sort.desc() ? " DESC" : " ASC") + " LIMIT ? OFFSET ?",
        (rs, rowNum) -> mapWorkItem(rs),
        args.toArray()
    );
  }

  List<WorkItem> duplicateCandidates(int limit) {
    return jdbc.query("""
        SELECT id, number, type, title, description, service, state, priority,
               impact, urgency, assignee_id, requester_id, team_id,
               resolution_code, resolution_notes, escalated, created_at, updated_at, closed_at, version
        FROM work_item WHERE org_id=? AND state NOT IN ('CLOSED','CANCELLED')
        ORDER BY updated_at DESC LIMIT ?
        """, (rs,row) -> mapWorkItem(rs), OrganizationContext.current(), Math.min(Math.max(limit,1),500));
  }

  void insertComment(WorkItemComment comment) {
    jdbc.update(
        """
        INSERT INTO work_item_comment (id, work_item_id, author_id, body, internal, created_at)
        VALUES (?,?,?,?,?,?)
        """,
        comment.id(),
        comment.workItemId(),
        comment.authorId(),
        comment.body(),
        comment.internal(),
        Timestamp.from(comment.createdAt())
    );
  }

  List<WorkItemComment> listComments(UUID workItemId, boolean includeInternal) {
    return jdbc.query(
        """
        SELECT id, work_item_id, author_id, body, internal, created_at
        FROM work_item_comment
        WHERE work_item_id = ? AND (? OR NOT internal)
        ORDER BY created_at ASC
        """,
        (rs, rowNum) -> new WorkItemComment(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("work_item_id"),
            rs.getString("author_id"),
            rs.getString("body"),
            rs.getBoolean("internal"),
            rs.getTimestamp("created_at").toInstant()
        ),
        workItemId,
        includeInternal
    );
  }

  void addWatcher(UUID workItemId, String subjectId, Instant watchedAt) {
    jdbc.update(
        """
        INSERT INTO work_item_watcher (work_item_id, subject_id, watched_at)
        VALUES (?,?,?)
        ON CONFLICT (work_item_id, subject_id) DO NOTHING
        """,
        workItemId,
        subjectId,
        Timestamp.from(watchedAt)
    );
  }

  boolean removeWatcher(UUID workItemId, String subjectId) {
    int n = jdbc.update(
        "DELETE FROM work_item_watcher WHERE work_item_id = ? AND subject_id = ?",
        workItemId,
        subjectId
    );
    return n > 0;
  }

  List<String> listWatchers(UUID workItemId) {
    return jdbc.query(
        """
        SELECT subject_id FROM work_item_watcher
        WHERE work_item_id = ?
        ORDER BY watched_at ASC
        """,
        (rs, i) -> rs.getString("subject_id"),
        workItemId
    );
  }

  boolean isWatching(UUID workItemId, String subjectId) {
    Integer n = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM work_item_watcher
        WHERE work_item_id = ? AND subject_id = ?
        """,
        Integer.class,
        workItemId,
        subjectId
    );
    return n != null && n > 0;
  }

  long countOpen() {
    Long n = jdbc.queryForObject(
        """
        SELECT count(*) FROM work_item
        WHERE org_id = ? AND state NOT IN ('CLOSED', 'CANCELLED')
        """,
        Long.class,
        OrganizationContext.current()
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
            AND wi.org_id = ?
            AND wi.state NOT IN ('CLOSED', 'CANCELLED')
          """,
          Long.class,
          OrganizationContext.current()
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
            AND wi.org_id = ?
            AND wi.state NOT IN ('CLOSED', 'CANCELLED')
          """,
          Long.class,
          OrganizationContext.current()
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
        rs.getBoolean("escalated"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        closedAt,
        rs.getLong("version")
    );
  }

  private static SqlAndArgs buildWhere(WorkItemQuery.Filter filter, String organizationId) {
    StringBuilder sql = new StringBuilder(" WHERE org_id = ?");
    List<Object> args = new ArrayList<>();
    args.add(organizationId);
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
    if (filter.requesterId() != null && !filter.requesterId().isBlank()) {
      sql.append(" AND requester_id = ?");
      args.add(filter.requesterId());
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
