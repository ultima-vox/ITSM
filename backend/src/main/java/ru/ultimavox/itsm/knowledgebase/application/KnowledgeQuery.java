package ru.ultimavox.itsm.knowledgebase.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeQuery {
  private final JdbcTemplate jdbc;

  public KnowledgeQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ArticleSummary> searchPublished(String titleQuery, String locale) {
    return search(titleQuery, locale, true);
  }

  /**
   * @param publishedOnly when true only PUBLISHED; when false all statuses (CMS authoring).
   */
  public List<ArticleSummary> search(String titleQuery, String locale, boolean publishedOnly) {
    String loc = locale == null || locale.isBlank() ? "ru" : locale;
    String q = blankToNull(titleQuery);
    String statusSql = publishedOnly ? " AND a.status = 'PUBLISHED' " : "";
    return jdbc.query(
        """
            SELECT a.id, a.number, a.slug, a.status, a.version, a.owner_subject, a.next_review_at,
                   r.title, r.summary, r.locale
            FROM knowledge_article a
            JOIN knowledge_article_revision r
              ON r.article_id = a.id AND r.version = a.version AND r.locale = ?
            WHERE 1=1
            """
            + statusSql
            + """
              AND (?::text IS NULL OR r.title ILIKE '%' || ? || '%')
            ORDER BY a.updated_at DESC, r.title
            """,
        (rs, i) -> new ArticleSummary(
            (UUID) rs.getObject("id"),
            rs.getString("number"),
            rs.getString("slug"),
            rs.getString("status"),
            rs.getInt("version"),
            rs.getString("owner_subject"),
            rs.getTimestamp("next_review_at") == null ? null : rs.getTimestamp("next_review_at").toInstant(),
            rs.getString("title"),
            rs.getString("summary"),
            rs.getString("locale")
        ),
        loc, q, q
    );
  }

  public Optional<ArticleDetail> findByIdOrSlug(String idOrSlug, String locale) {
    String loc = locale == null || locale.isBlank() ? "ru" : locale;
    UUID asUuid = tryUuid(idOrSlug);
    List<ArticleDetail> rows = jdbc.query(
        """
            SELECT a.id, a.number, a.slug, a.status, a.version, a.owner_subject, a.next_review_at,
                   r.title, r.summary, r.body, r.locale, r.author_subject, r.created_at
            FROM knowledge_article a
            JOIN knowledge_article_revision r
              ON r.article_id = a.id AND r.version = a.version AND r.locale = ?
            WHERE (a.id = ? OR a.slug = ?)
            """,
        (rs, i) -> new ArticleDetail(
            (UUID) rs.getObject("id"),
            rs.getString("number"),
            rs.getString("slug"),
            rs.getString("status"),
            rs.getInt("version"),
            rs.getString("owner_subject"),
            rs.getTimestamp("next_review_at") == null ? null : rs.getTimestamp("next_review_at").toInstant(),
            rs.getString("title"),
            rs.getString("summary"),
            rs.getString("body"),
            rs.getString("locale"),
            rs.getString("author_subject"),
            rs.getTimestamp("created_at").toInstant()
        ),
        loc, asUuid, idOrSlug
    );
    return rows.stream().findFirst();
  }

  private static UUID tryUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (Exception ex) {
      return null;
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record ArticleSummary(
      UUID id,
      String number,
      String slug,
      String status,
      int version,
      String ownerSubject,
      Instant nextReviewAt,
      String title,
      String summary,
      String locale
  ) {}

  public record ArticleDetail(
      UUID id,
      String number,
      String slug,
      String status,
      int version,
      String ownerSubject,
      Instant nextReviewAt,
      String title,
      String summary,
      String body,
      String locale,
      String authorSubject,
      Instant revisionCreatedAt
  ) {}
}
