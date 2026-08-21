package ru.ultimavox.itsm.cmdb.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.cmdb.CmdbReportQuery;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Service
final class JdbcCmdbReportQuery implements CmdbReportQuery {
  private final JdbcTemplate jdbc;

  JdbcCmdbReportQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Snapshot snapshot() {
    String org = OrganizationContext.current();
    long cis = count("SELECT count(*) FROM configuration_item WHERE org_id = ?", org);
    long orphans = count("""
        SELECT count(*) FROM configuration_item ci
        WHERE ci.org_id = ? AND NOT EXISTS (
          SELECT 1 FROM ci_relationship r
          WHERE r.org_id = ci.org_id AND (r.source_ci_id = ci.id OR r.target_ci_id = ci.id)
        )
        """, org);
    long relationships = count("SELECT count(*) FROM ci_relationship WHERE org_id = ?", org);
    return new Snapshot(cis, orphans, relationships);
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0L : value;
  }
}
