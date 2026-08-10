package ru.ultimavox.itsm.problemmanagement.application;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.problemmanagement.domain.Problem;

@Service
public class ProblemQuery {
  private final JdbcTemplate jdbc;

  public ProblemQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ProblemSummary> list(String status) {
    String statusFilter = status == null || status.isBlank() ? null : status;
    return jdbc.query(
        """
            SELECT id, number, title, status, root_cause, workaround, resolution, created_at, updated_at
            FROM problem
            WHERE org_id = ? AND (?::text IS NULL OR status = ?)
            ORDER BY updated_at DESC
            """,
        (rs, i) -> new ProblemSummary(
            (UUID) rs.getObject("id"),
            rs.getString("number"),
            rs.getString("title"),
            rs.getString("status"),
            rs.getString("root_cause"),
            rs.getString("workaround"),
            rs.getString("resolution"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        ),
        OrganizationContext.current(), statusFilter, statusFilter
    );
  }

  public Optional<Problem> findById(UUID id) {
    List<Problem> rows = jdbc.query(
        "SELECT id, number, title, status, root_cause, workaround, resolution FROM problem WHERE id = ? AND org_id = ?",
        (rs, i) -> new Problem(
            (UUID) rs.getObject("id"),
            rs.getString("number"),
            rs.getString("title"),
            Problem.Status.valueOf(rs.getString("status")),
            rs.getString("root_cause"),
            rs.getString("workaround"),
            rs.getString("resolution"),
            loadLinks((UUID) rs.getObject("id"))
        ),
        id, OrganizationContext.current()
    );
    return rows.stream().findFirst();
  }

  private Set<UUID> loadLinks(UUID problemId) {
    List<UUID> ids = jdbc.query(
        "SELECT work_item_id FROM problem_work_item WHERE problem_id = ?",
        (rs, i) -> (UUID) rs.getObject("work_item_id"),
        problemId
    );
    return new HashSet<>(ids);
  }

  public record ProblemSummary(
      UUID id,
      String number,
      String title,
      String status,
      String rootCause,
      String workaround,
      String resolution,
      Instant createdAt,
      Instant updatedAt
  ) {}
}
