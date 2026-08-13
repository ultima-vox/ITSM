package ru.ultimavox.itsm.servicedesk.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.search.SearchDocument;
import ru.ultimavox.itsm.platform.search.SearchReindexSource;

/**
 * Search reconciliation source for service desk work items. Reads the authoritative
 * {@code work_item} table so the projection can be rebuilt on demand. Mirrors the document
 * shape produced by {@link WorkItemSearchIndexer}.
 */
@Component
public class WorkItemSearchReindexSource implements SearchReindexSource {

  private final JdbcTemplate jdbc;

  public WorkItemSearchReindexSource(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public String sourceName() {
    return "work-item";
  }

  @Override
  public List<String> organizationIds() {
    return jdbc.queryForList("SELECT DISTINCT org_id FROM work_item ORDER BY org_id", String.class);
  }

  @Override
  public List<SearchDocument> snapshotPage(String organizationId, int offset, int limit) {
    return jdbc.query(
        """
            SELECT id, number, type, title, description, service, state, priority, updated_at
            FROM work_item
            WHERE org_id = ?
            ORDER BY updated_at DESC
            OFFSET ? LIMIT ?
            """,
        (rs, rowNum) -> map(rs),
        organizationId, offset, limit);
  }

  private SearchDocument map(ResultSet rs) throws SQLException {
    String number = rs.getString("number");
    String type = rs.getString("type");
    String title = rs.getString("title");
    String description = rs.getString("description");
    Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
    return new SearchDocument(
        rs.getObject("id", UUID.class).toString(),
        "work-item",
        number + " · " + title,
        description == null ? "" : description,
        Set.of("work-item", type.toLowerCase(java.util.Locale.ROOT)),
        updatedAt,
        Map.of(
            "number", number,
            "state", rs.getString("state"),
            "priority", rs.getString("priority"),
            "service", rs.getString("service")));
  }
}
