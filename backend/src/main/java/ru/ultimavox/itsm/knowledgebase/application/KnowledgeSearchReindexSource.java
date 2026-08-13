package ru.ultimavox.itsm.knowledgebase.application;

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
 * Search reconciliation source for knowledge articles. Reads the authoritative
 * {@code knowledge_article} / {@code knowledge_article_revision} tables so the projection can
 * be rebuilt on demand. Uses the current revision's primary locale (ru) per article.
 */
@Component
public class KnowledgeSearchReindexSource implements SearchReindexSource {

  private static final String PRIMARY_LOCALE = "ru";

  private final JdbcTemplate jdbc;

  public KnowledgeSearchReindexSource(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public String sourceName() {
    return "knowledge-article";
  }

  @Override
  public List<String> organizationIds() {
    return jdbc.queryForList(
        "SELECT DISTINCT org_id FROM knowledge_article ORDER BY org_id", String.class);
  }

  @Override
  public List<SearchDocument> snapshotPage(String organizationId, int offset, int limit) {
    return jdbc.query(
        """
            SELECT a.id, a.number, a.status, a.updated_at, r.title, r.summary, r.body
            FROM knowledge_article a
            JOIN knowledge_article_revision r
              ON r.article_id = a.id
             AND r.version = a.version
             AND r.locale = ?
            WHERE a.org_id = ?
            ORDER BY a.updated_at DESC
            OFFSET ? LIMIT ?
            """,
        (rs, rowNum) -> map(rs),
        PRIMARY_LOCALE, organizationId, offset, limit);
  }

  private SearchDocument map(ResultSet rs) throws SQLException {
    String summary = rs.getString("summary");
    String body = rs.getString("body");
    Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
    String status = rs.getString("status");
    return new SearchDocument(
        rs.getObject("id", UUID.class).toString(),
        "knowledge-article",
        rs.getString("title"),
        join(summary, body),
        Set.of("knowledge-article", status.toLowerCase(java.util.Locale.ROOT)),
        updatedAt,
        Map.of(
            "number", rs.getString("number"),
            "status", status));
  }

  private static String join(String summary, String body) {
    String head = summary == null ? "" : summary;
    String rest = body == null ? "" : body;
    return (head.isBlank() || rest.isBlank()) ? head + rest : head + "\n\n" + rest;
  }
}
