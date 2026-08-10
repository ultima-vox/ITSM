package ru.ultimavox.itsm.knowledgebase.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Service
public class RecordHelpfulnessVote {
  private final JdbcTemplate jdbc;
  private final KnowledgeQuery query;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public RecordHelpfulnessVote(
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
  public Voted vote(UUID articleId, boolean helpful, String comment, String actor) {
    var article = query.findByIdOrSlug(articleId.toString(), "ru")
        .orElseThrow(() -> new IllegalArgumentException("Article not found: " + articleId));
    if (!"PUBLISHED".equals(article.status())) {
      throw new IllegalStateException("Only published articles accept feedback");
    }

    UUID feedbackId = UUID.randomUUID();
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();

    jdbc.update(
        """
            INSERT INTO knowledge_feedback (id, article_id, revision, subject_id, helpful, comment, created_at)
            VALUES (?,?,?,?,?,?,?)
            """,
        feedbackId, articleId, article.version(), actor, helpful, comment, java.sql.Timestamp.from(now)
    );

    Map<String, Object> state = Map.of(
        "articleId", articleId.toString(),
        "revision", article.version(),
        "helpful", helpful
    );
    audit.append(new AuditTrail.Entry(
        actor, "knowledge.feedback", "knowledge-article", articleId.toString(),
        Map.of(), state, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "knowledge.feedback.recorded", 1, now, correlationId,
        "knowledge-article", articleId.toString(), state
    ));
    return new Voted(feedbackId, articleId, article.version(), helpful);
  }

  public record Voted(UUID feedbackId, UUID articleId, int revision, boolean helpful) {}
}
