package ru.ultimavox.itsm.changemanagement.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.changemanagement.domain.Change;

@Service
public class ChangeQuery {
  private final JdbcTemplate jdbc;

  public ChangeQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Change> list(String status) {
    String statusFilter = status == null || status.isBlank() ? null : status;
    return jdbc.query(
        """
            SELECT id, number, type, risk, status, title, planned_start, planned_end,
                   implementation_plan, rollback_plan, business_justification, cab_notes, cab_risk_level
            FROM change_request
            WHERE (? IS NULL OR status = ?)
            ORDER BY updated_at DESC
            """,
        (rs, i) -> map(rs),
        statusFilter, statusFilter
    );
  }

  public Optional<Change> findById(UUID id) {
    List<Change> rows = jdbc.query(
        """
            SELECT id, number, type, risk, status, title, planned_start, planned_end,
                   implementation_plan, rollback_plan, business_justification, cab_notes, cab_risk_level
            FROM change_request WHERE id = ?
            """,
        (rs, i) -> map(rs),
        id
    );
    return rows.stream().findFirst();
  }

  private static Change map(java.sql.ResultSet rs) throws java.sql.SQLException {
    String cabRisk = rs.getString("cab_risk_level");
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
        rs.getString("business_justification"),
        rs.getString("cab_notes"),
        cabRisk == null ? null : Change.Risk.valueOf(cabRisk)
    );
  }

  private static Instant toInstant(java.sql.Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }
}
