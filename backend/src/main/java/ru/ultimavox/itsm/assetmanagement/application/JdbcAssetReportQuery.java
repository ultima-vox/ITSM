package ru.ultimavox.itsm.assetmanagement.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.assetmanagement.AssetReportQuery;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Service
final class JdbcAssetReportQuery implements AssetReportQuery {
  private final JdbcTemplate jdbc;

  JdbcAssetReportQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Snapshot snapshot() {
    String org = OrganizationContext.current();
    return new Snapshot(
        count("SELECT count(*) FROM asset WHERE org_id = ?", org),
        count("SELECT count(*) FROM asset WHERE org_id = ? AND status = 'IN_USE'", org),
        count("SELECT count(*) FROM asset WHERE org_id = ? AND status = 'IN_STOCK'", org)
    );
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0L : value;
  }
}
