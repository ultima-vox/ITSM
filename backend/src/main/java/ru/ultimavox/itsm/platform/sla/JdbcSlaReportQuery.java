package ru.ultimavox.itsm.platform.sla;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Service
final class JdbcSlaReportQuery implements SlaReportQuery {
  private final JdbcTemplate jdbc;

  JdbcSlaReportQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Snapshot snapshot() {
    return new Snapshot(
        count("SELECT count(DISTINCT aggregate_id) FROM sla_clock WHERE org_id = ? AND state = 'BREACHED'"),
        count("""
            SELECT count(DISTINCT aggregate_id) FROM sla_clock
            WHERE org_id = ? AND state = 'RUNNING' AND warning_at IS NOT NULL
              AND warning_at <= now() AND due_at > now()
            """));
  }

  private long count(String sql) {
    Long value = jdbc.queryForObject(sql, Long.class, OrganizationContext.current());
    return value == null ? 0L : value;
  }
}
