package ru.ultimavox.itsm.changemanagement.application;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.changemanagement.ChangeCatalogQuery;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Service
final class JdbcChangeCatalogQuery implements ChangeCatalogQuery {
  private static final int MAX_IDS = 500;

  private final JdbcTemplate jdbc;

  JdbcChangeCatalogQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<ChangeSummary> summaries(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    List<UUID> distinct = new LinkedHashSet<>(ids).stream().limit(MAX_IDS).toList();
    String placeholders = String.join(", ", java.util.Collections.nCopies(distinct.size(), "?"));
    Object[] args = new Object[distinct.size() + 1];
    args[0] = OrganizationContext.current();
    for (int i = 0; i < distinct.size(); i++) {
      args[i + 1] = distinct.get(i);
    }
    return jdbc.query(
        """
            SELECT id, number, title, type, status, planned_start, planned_end
            FROM change_request
            WHERE org_id = ? AND id IN (%s)
            ORDER BY number
            """.formatted(placeholders),
        (rs, row) -> new ChangeSummary(
            rs.getObject("id", UUID.class),
            rs.getString("number"),
            rs.getString("title"),
            rs.getString("type"),
            rs.getString("status"),
            rs.getTimestamp("planned_start") == null ? null : rs.getTimestamp("planned_start").toInstant(),
            rs.getTimestamp("planned_end") == null ? null : rs.getTimestamp("planned_end").toInstant()
        ),
        args
    );
  }
}
