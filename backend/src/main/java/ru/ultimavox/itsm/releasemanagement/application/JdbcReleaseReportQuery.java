package ru.ultimavox.itsm.releasemanagement.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.releasemanagement.ReleaseReportQuery;

@Service
final class JdbcReleaseReportQuery implements ReleaseReportQuery {
  private final JdbcTemplate jdbc;

  JdbcReleaseReportQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Snapshot snapshot() {
    String org = OrganizationContext.current();
    long inFlight = count("""
        SELECT count(*) FROM release_record
        WHERE org_id = ? AND status IN ('PLANNING', 'BUILD', 'TESTING', 'GO_NO_GO', 'DEPLOYING')
        """, org);
    long deployed = count("""
        SELECT count(*) FROM release_record WHERE org_id = ? AND status IN ('DEPLOYED', 'CLOSED')
        """, org);
    long rolledBack = count("""
        SELECT count(*) FROM release_record WHERE org_id = ? AND status = 'ROLLED_BACK'
        """, org);
    long decided = deployed + rolledBack;
    Double successRate = decided == 0 ? null : Math.round(deployed * 1000.0 / decided) / 10.0;
    return new Snapshot(inFlight, deployed, rolledBack, successRate);
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0L : value;
  }
}
