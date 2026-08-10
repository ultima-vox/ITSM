package ru.ultimavox.itsm.knowledgebase.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Service
public class KnowledgeCommands {

  private final JdbcTemplate jdbc;
  private final KnowledgeQuery query;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public KnowledgeCommands(
      JdbcTemplate jdbc,
      KnowledgeQuery query,
      AuditTrail audit,
      IntegrationEventOutbox outbox
  ) {
    this.jdbc = jdbc;
    this.query = query;
    this.audit = audit;
    this.outbox = outbox;
  }

  @Transactional
  public KnowledgeQuery.ArticleDetail create(CreateCommand command, String actor) {
    if (command.title() == null || command.title().isBlank()) {
      throw new IllegalArgumentException("title is required");
    }
    if (command.body() == null || command.body().isBlank()) {
      throw new IllegalArgumentException("body is required");
    }
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    Long seq = jdbc.queryForObject("SELECT nextval('knowledge_number_seq')", Long.class);
    String number = "KB-%06d".formatted(seq == null ? 1 : seq);
    String slug = slugify(command.slug() != null ? command.slug() : command.title(), number);
    String locale = blankTo(command.locale(), "ru");
    String summary = blankTo(command.summary(), truncate(command.body(), 240));

    jdbc.update(
        """
        INSERT INTO knowledge_article (id, number, status, version, owner_subject, next_review_at, slug, created_at, updated_at)
        VALUES (?,?,?,?,?,?,?,?,?)
        """,
        id,
        number,
        "DRAFT",
        1,
        actor,
        null,
        slug,
        Timestamp.from(now),
        Timestamp.from(now)
    );
    jdbc.update(
        """
        INSERT INTO knowledge_article_revision (id, article_id, version, locale, title, summary, body, author_subject, created_at)
        VALUES (?,?,?,?,?,?,?,?,?)
        """,
        UUID.randomUUID(),
        id,
        1,
        locale,
        command.title().trim(),
        summary,
        command.body().trim(),
        actor,
        Timestamp.from(now)
    );

    Map<String, Object> after = Map.of(
        "number", number,
        "slug", slug,
        "status", "DRAFT",
        "version", 1,
        "title", command.title().trim()
    );
    audit.append(new AuditTrail.Entry(
        actor, "knowledge.created", "knowledge-article", id.toString(),
        Map.of(), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "knowledge.created", 1, now, correlationId,
        "knowledge-article", id.toString(), after
    ));
    return query.findByIdOrSlug(id.toString(), locale)
        .orElseThrow(() -> new IllegalStateException("Created article not readable"));
  }

  @Transactional
  public KnowledgeQuery.ArticleDetail update(UUID id, UpdateCommand command, String actor) {
    KnowledgeQuery.ArticleDetail current = query.findByIdOrSlug(id.toString(), blankTo(command.locale(), "ru"))
        .orElseThrow(() -> new IllegalArgumentException("Article not found: " + id));
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    String locale = blankTo(command.locale(), blankTo(current.locale(), "ru"));
    String status = current.status();

    if ("PUBLISHED".equals(status) || "ARCHIVED".equals(status)) {
      // New draft revision
      int nextVersion = current.version() + 1;
      String title = command.title() != null ? command.title().trim() : current.title();
      String body = command.body() != null ? command.body().trim() : current.body();
      String summary = command.summary() != null
          ? command.summary().trim()
          : (current.summary() != null ? current.summary() : truncate(body, 240));
      if (title.isBlank() || body.isBlank()) {
        throw new IllegalArgumentException("title and body are required");
      }
      jdbc.update(
          """
          INSERT INTO knowledge_article_revision (id, article_id, version, locale, title, summary, body, author_subject, created_at)
          VALUES (?,?,?,?,?,?,?,?,?)
          """,
          UUID.randomUUID(), id, nextVersion, locale, title, summary, body, actor, Timestamp.from(now)
      );
      jdbc.update(
          """
          UPDATE knowledge_article
          SET status = 'DRAFT', version = ?, updated_at = ?
          WHERE id = ?
          """,
          nextVersion, Timestamp.from(now), id
      );
    } else {
      // Edit current draft / in-review revision in place
      String title = command.title() != null ? command.title().trim() : current.title();
      String body = command.body() != null ? command.body().trim() : current.body();
      String summary = command.summary() != null
          ? command.summary().trim()
          : (current.summary() != null ? current.summary() : truncate(body, 240));
      if (title.isBlank() || body.isBlank()) {
        throw new IllegalArgumentException("title and body are required");
      }
      int updated = jdbc.update(
          """
          UPDATE knowledge_article_revision
          SET title = ?, summary = ?, body = ?, author_subject = ?
          WHERE article_id = ? AND version = ? AND locale = ?
          """,
          title, summary, body, actor, id, current.version(), locale
      );
      if (updated == 0) {
        jdbc.update(
            """
            INSERT INTO knowledge_article_revision (id, article_id, version, locale, title, summary, body, author_subject, created_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            """,
            UUID.randomUUID(), id, current.version(), locale, title, summary, body, actor, Timestamp.from(now)
        );
      }
      jdbc.update("UPDATE knowledge_article SET updated_at = ? WHERE id = ?", Timestamp.from(now), id);
    }

    Map<String, Object> after = Map.of(
        "title", command.title() != null ? command.title() : current.title(),
        "versionNote", command.versionNote() == null ? "" : command.versionNote()
    );
    audit.append(new AuditTrail.Entry(
        actor, "knowledge.updated", "knowledge-article", id.toString(),
        Map.of("version", current.version()), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "knowledge.updated", 1, now, correlationId,
        "knowledge-article", id.toString(), after
    ));
    return query.findByIdOrSlug(id.toString(), locale)
        .orElseThrow(() -> new IllegalStateException("Updated article not readable"));
  }

  @Transactional
  public KnowledgeQuery.ArticleDetail publish(UUID id, String actor) {
    KnowledgeQuery.ArticleDetail current = query.findByIdOrSlug(id.toString(), "ru")
        .orElseThrow(() -> new IllegalArgumentException("Article not found: " + id));
    if ("PUBLISHED".equals(current.status())) {
      return current;
    }
    if (current.title() == null || current.title().isBlank()
        || current.body() == null || current.body().isBlank()) {
      throw new IllegalStateException("Primary Russian content is required before publication");
    }
    Instant now = Instant.now();
    Instant reviewAt = now.plus(180, ChronoUnit.DAYS);
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();

    jdbc.update(
        """
        UPDATE knowledge_article
        SET status = 'PUBLISHED', next_review_at = ?, updated_at = ?
        WHERE id = ?
        """,
        Timestamp.from(reviewAt),
        Timestamp.from(now),
        id
    );

    Map<String, Object> after = Map.of(
        "status", "PUBLISHED",
        "version", current.version(),
        "nextReviewAt", reviewAt.toString()
    );
    audit.append(new AuditTrail.Entry(
        actor, "knowledge.published", "knowledge-article", id.toString(),
        Map.of("status", current.status()), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "knowledge.published", 1, now, correlationId,
        "knowledge-article", id.toString(), after
    ));
    return query.findByIdOrSlug(id.toString(), "ru")
        .orElseThrow(() -> new IllegalStateException("Published article not readable"));
  }

  private static String slugify(String raw, String fallback) {
    String base = raw == null ? "" : raw.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9\\s-]", "")
        .trim()
        .replaceAll("\\s+", "-");
    if (base.isBlank()) {
      base = fallback.toLowerCase(Locale.ROOT);
    }
    if (base.length() > 180) {
      base = base.substring(0, 180);
    }
    return base;
  }

  private static String blankTo(String value, String def) {
    return value == null || value.isBlank() ? def : value;
  }

  private static String truncate(String body, int max) {
    if (body == null) {
      return "";
    }
    String t = body.trim();
    return t.length() <= max ? t : t.substring(0, max) + "…";
  }

  public record CreateCommand(
      String title,
      String body,
      String summary,
      String slug,
      String locale
  ) {}

  public record UpdateCommand(
      String title,
      String body,
      String summary,
      String locale,
      String versionNote
  ) {}
}
