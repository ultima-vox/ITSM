package ru.ultimavox.itsm.changemanagement.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.changemanagement.ChangeReportQuery;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Service
final class JdbcChangeReportQuery implements ChangeReportQuery {
  private final JdbcTemplate jdbc;

  JdbcChangeReportQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Snapshot snapshot() {
    String org = OrganizationContext.current();
    long open = count("""
        SELECT count(*) FROM change_request
        WHERE org_id = ? AND status NOT IN ('CLOSED', 'REJECTED')
        """, org);
    long closed = count("""
        SELECT count(*) FROM change_request WHERE org_id = ? AND status = 'CLOSED'
        """, org);
    long rejected = count("""
        SELECT count(*) FROM change_request WHERE org_id = ? AND status = 'REJECTED'
        """, org);
    long decided = closed + rejected;
    Double successRate = decided == 0 ? null : Math.round(closed * 1000.0 / decided) / 10.0;
    return new Snapshot(open, closed, rejected, successRate);
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0L : value;
  }
}
