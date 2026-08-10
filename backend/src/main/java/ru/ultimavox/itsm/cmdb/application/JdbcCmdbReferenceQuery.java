package ru.ultimavox.itsm.cmdb.application;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.cmdb.CmdbReferenceQuery;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Service
final class JdbcCmdbReferenceQuery implements CmdbReferenceQuery {
  private final JdbcTemplate jdbc;

  JdbcCmdbReferenceQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean exists(UUID configurationItemId) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM configuration_item WHERE id = ? AND org_id = ?",
        Integer.class,
        configurationItemId,
        OrganizationContext.current());
    return count != null && count > 0;
  }
}
