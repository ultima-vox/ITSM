package ru.ultimavox.itsm.servicedesk.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItemComment;

@Service
public class AddWorkItemComment {

  private final WorkItemStore store;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  AddWorkItemComment(WorkItemStore store, AuditTrail audit, IntegrationEventOutbox outbox) {
    this.store = store;
    this.audit = audit;
    this.outbox = outbox;
  }

  @Transactional
  public WorkItemComment add(UUID workItemId, Command command, String actorId) {
    if (command.body() == null || command.body().isBlank()) {
      throw new IllegalArgumentException("comment body is required");
    }
    store.requireById(workItemId);

    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();
    WorkItemComment comment = new WorkItemComment(
        UUID.randomUUID(),
        workItemId,
        actorId,
        command.body().trim(),
        now
    );
    store.insertComment(comment);

    Map<String, Object> after = Map.of(
        "commentId", comment.id().toString(),
        "body", comment.body(),
        "authorId", actorId
    );
    audit.append(new AuditTrail.Entry(
        actorId,
        "work-item.comment-added",
        "work-item",
        workItemId.toString(),
        Map.of(),
        after,
        correlationId,
        now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(),
        "work-item.comment-added",
        1,
        now,
        correlationId,
        "work-item",
        workItemId.toString(),
        after
    ));
    return comment;
  }

  public record Command(String body) {}
}
