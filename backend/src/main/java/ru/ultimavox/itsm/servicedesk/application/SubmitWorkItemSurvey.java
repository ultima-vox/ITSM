package ru.ultimavox.itsm.servicedesk.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;

@Service
public class SubmitWorkItemSurvey {
  private final WorkItemStore store;
  private final JdbcTemplate jdbc;
  private final AuditTrail audit;

  SubmitWorkItemSurvey(WorkItemStore store, JdbcTemplate jdbc, AuditTrail audit) {
    this.store = store;
    this.jdbc = jdbc;
    this.audit = audit;
  }

  @Transactional
  public Result submit(UUID workItemId, int rating, String comment, String actorId) {
    if (rating < 1 || rating > 5) throw new IllegalArgumentException("rating must be between 1 and 5");
    var item = store.requireById(workItemId);
    if (!actorId.equals(item.requesterId())) throw new AccessDeniedException("Only requester may submit survey");
    if (item.state() != State.RESOLVED && item.state() != State.CLOSED) {
      throw new IllegalStateException("Survey requires resolved or closed work item");
    }
    Instant now = Instant.now();
    String normalized = comment == null || comment.isBlank() ? null : comment.trim();
    try {
      jdbc.update("INSERT INTO work_item_survey (id,org_id,work_item_id,respondent_id,rating,comment,submitted_at) VALUES (?,?,?,?,?,?,?)",
          UUID.randomUUID(), OrganizationContext.current(), workItemId, actorId, rating, normalized, Timestamp.from(now));
    } catch (DuplicateKeyException ex) {
      throw new IllegalStateException("Survey already submitted");
    }
    audit.append(new AuditTrail.Entry(actorId, "work-item.survey-submitted", "work-item",
        workItemId.toString(), Map.of(), Map.of("rating", rating), CorrelationContext.currentOrCreate(), now));
    return new Result(workItemId, rating, normalized, now);
  }

  public record Result(UUID workItemId, int rating, String comment, Instant submittedAt) {}
}
