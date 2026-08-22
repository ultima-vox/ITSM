package ru.ultimavox.itsm.releasemanagement.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.releasemanagement.domain.Release;

@Service
public class ReleaseQuery {
  private static final String COLUMNS = """
      id, number, name, type, status, description, deployment_plan, rollback_plan, test_summary,
      go_decision, go_decision_notes, go_decided_by, go_decided_at, release_manager,
      planned_start, planned_end, actual_start, actual_end, version
      """;

  private final JdbcTemplate jdbc;

  public ReleaseQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Release> list(String status, String type, String q) {
    StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM release_record WHERE org_id = ?");
    List<Object> args = new ArrayList<>();
    args.add(OrganizationContext.current());
    if (status != null && !status.isBlank()) {
      sql.append(" AND status = ?");
      args.add(status.trim().toUpperCase());
    }
    if (type != null && !type.isBlank()) {
      sql.append(" AND type = ?");
      args.add(type.trim().toUpperCase());
    }
    if (q != null && !q.isBlank()) {
      sql.append(" AND (lower(number) LIKE ? OR lower(name) LIKE ?)");
      String pattern = "%" + q.trim().toLowerCase() + "%";
      args.add(pattern);
      args.add(pattern);
    }
    sql.append(" ORDER BY updated_at DESC");
    return jdbc.query(sql.toString(), (rs, row) -> map(rs), args.toArray());
  }

  public Optional<Release> findById(UUID id) {
    return jdbc.query(
        "SELECT " + COLUMNS + " FROM release_record WHERE id = ? AND org_id = ?",
        (rs, row) -> map(rs),
        id, OrganizationContext.current()
    ).stream().findFirst();
  }

  /** Releases whose planned window overlaps {@code [start, end)}, ignoring cancelled work. */
  public List<Release> findScheduleConflicts(Instant start, Instant end, UUID excludeId) {
    if (start == null || end == null || !end.isAfter(start)) {
      return List.of();
    }
    return jdbc.query(
        "SELECT " + COLUMNS + """
             FROM release_record
             WHERE org_id = ?
               AND status NOT IN ('CANCELLED', 'CLOSED')
               AND planned_start IS NOT NULL
               AND planned_end IS NOT NULL
               AND planned_start < ?
               AND planned_end > ?
               AND (?::uuid IS NULL OR id <> ?)
             ORDER BY planned_start
            """,
        (rs, row) -> map(rs),
        OrganizationContext.current(),
        java.sql.Timestamp.from(end),
        java.sql.Timestamp.from(start),
        excludeId,
        excludeId
    );
  }

  public List<UUID> changeIds(UUID releaseId) {
    return jdbc.queryForList(
        "SELECT change_id FROM release_change WHERE release_id = ? AND org_id = ? ORDER BY added_at",
        UUID.class,
        releaseId, OrganizationContext.current()
    );
  }

  static Release map(ResultSet rs) throws SQLException {
    String goDecision = rs.getString("go_decision");
    return new Release(
        rs.getObject("id", UUID.class),
        rs.getString("number"),
        rs.getString("name"),
        Release.Type.valueOf(rs.getString("type")),
        Release.Status.valueOf(rs.getString("status")),
        rs.getString("description"),
        rs.getString("deployment_plan"),
        rs.getString("rollback_plan"),
        rs.getString("test_summary"),
        goDecision == null ? null : Release.GoDecision.valueOf(goDecision),
        rs.getString("go_decision_notes"),
        rs.getString("go_decided_by"),
        instant(rs, "go_decided_at"),
        rs.getString("release_manager"),
        instant(rs, "planned_start"),
        instant(rs, "planned_end"),
        instant(rs, "actual_start"),
        instant(rs, "actual_end"),
        rs.getLong("version")
    );
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }
}
