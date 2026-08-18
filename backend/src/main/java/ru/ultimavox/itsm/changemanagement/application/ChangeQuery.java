package ru.ultimavox.itsm.changemanagement.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.changemanagement.domain.Change;

@Service
public class ChangeQuery {
  private final JdbcTemplate jdbc;

  public ChangeQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Change> list(String status, String q) {
    String statusFilter = status == null || status.isBlank() ? null : status;
    StringBuilder sql = new StringBuilder(
        """
            SELECT id, number, type, risk, status, title, planned_start, planned_end,
                   implementation_plan, rollback_plan, test_plan, business_justification,
                   cab_notes, cab_risk_level, impact, version
            FROM change_request
            WHERE org_id = ? AND (?::text IS NULL OR status = ?)
        """);
    java.util.List<Object> args = new java.util.ArrayList<>();
    args.add(OrganizationContext.current());
    args.add(statusFilter); args.add(statusFilter);
    if (q != null && !q.isBlank()) {
        String pattern = "%" + q.trim().toLowerCase() + "%";
        sql.append(" AND (lower(number) LIKE ? OR lower(title) LIKE ?)");
        args.add(pattern); args.add(pattern);
    }
    sql.append(" ORDER BY updated_at DESC");
    return jdbc.query(sql.toString(), (rs, i) -> map(rs), args.toArray());
  }

  public Optional<Change> findById(UUID id) {
    List<Change> rows = jdbc.query(
        """
            SELECT id, number, type, risk, status, title, planned_start, planned_end,
                   implementation_plan, rollback_plan, test_plan, business_justification,
                   cab_notes, cab_risk_level, impact, version
            FROM change_request WHERE id = ? AND org_id = ?
            """,
        (rs, i) -> map(rs),
        id, OrganizationContext.current()
    );
    return rows.stream().findFirst();
  }

  /**
   * Changes whose planned window overlaps {@code [start, end]} (half-open overlap rule).
   * Excludes terminal REJECTED and the optional self id.
   */
  public List<Change> findScheduleConflicts(Instant start, Instant end, UUID excludeId) {
    if (start == null || end == null || !end.isAfter(start)) {
      return List.of();
    }
    return jdbc.query(
        """
            SELECT id, number, type, risk, status, title, planned_start, planned_end,
                   implementation_plan, rollback_plan, test_plan, business_justification,
                   cab_notes, cab_risk_level, impact, version
            FROM change_request
            WHERE org_id = ?
              AND status NOT IN ('REJECTED', 'CLOSED', 'DRAFT')
              AND planned_start IS NOT NULL
              AND planned_end IS NOT NULL
              AND planned_start < ?
              AND planned_end > ?
              AND (?::uuid IS NULL OR id <> ?)
            ORDER BY planned_start ASC
            """,
        (rs, i) -> map(rs),
        OrganizationContext.current(),
        java.sql.Timestamp.from(end),
        java.sql.Timestamp.from(start),
        excludeId,
        excludeId
    );
  }

  private static Change map(java.sql.ResultSet rs) throws java.sql.SQLException {
    String cabRisk = rs.getString("cab_risk_level");
    String impactStr = rs.getString("impact");
    return new Change(
        (UUID) rs.getObject("id"),
        rs.getString("number"),
        Change.Type.valueOf(rs.getString("type")),
        Change.Risk.valueOf(rs.getString("risk")),
        Change.Status.valueOf(rs.getString("status")),
        rs.getString("title"),
        toInstant(rs.getTimestamp("planned_start")),
        toInstant(rs.getTimestamp("planned_end")),
        rs.getString("implementation_plan"),
        rs.getString("rollback_plan"),
        rs.getString("test_plan"),
        rs.getString("business_justification"),
        rs.getString("cab_notes"),
        cabRisk == null ? null : Change.Risk.valueOf(cabRisk),
        impactStr == null ? null : Change.Impact.valueOf(impactStr),
        rs.getLong("version")
    );
  }

  private static Instant toInstant(java.sql.Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }
}
