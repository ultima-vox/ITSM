package ru.ultimavox.itsm.problemmanagement.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.problemmanagement.ProblemReportQuery;

@Service
final class JdbcProblemReportQuery implements ProblemReportQuery {
  private final JdbcTemplate jdbc;

  JdbcProblemReportQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Snapshot snapshot() {
    String org = OrganizationContext.current();
    return new Snapshot(
        count("""
            SELECT count(*) FROM problem
            WHERE org_id = ? AND status NOT IN ('RESOLVED', 'CLOSED')
            """, org),
        count("SELECT count(*) FROM problem WHERE org_id = ? AND status = 'KNOWN_ERROR'", org),
        count("SELECT count(*) FROM problem WHERE org_id = ? AND status IN ('RESOLVED', 'CLOSED')", org)
    );
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0L : value;
  }
}
