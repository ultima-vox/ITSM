package ru.ultimavox.itsm.servicedesk.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.notification.NotificationRequest;
import ru.ultimavox.itsm.platform.notification.NotificationService;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItemComment;

@Service
public class AddWorkItemComment {

  private static final Logger log = LoggerFactory.getLogger(AddWorkItemComment.class);

  private final WorkItemStore store;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final NotificationService notifications;

  AddWorkItemComment(
      WorkItemStore store,
      AuditTrail audit,
      IntegrationEventOutbox outbox,
      NotificationService notifications
  ) {
    this.store = store;
    this.audit = audit;
    this.outbox = outbox;
    this.notifications = notifications;
  }

  @Transactional
  public WorkItemComment add(UUID workItemId, Command command, String actorId) {
    if (command.body() == null || command.body().isBlank()) {
      throw new IllegalArgumentException("comment body is required");
    }
    WorkItem item = store.requireById(workItemId);

    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    WorkItemComment comment = new WorkItemComment(
        UUID.randomUUID(),
        workItemId,
        actorId,
        command.body().trim(),
        command.internal(),
        now
    );
    store.insertComment(comment);

    Map<String, Object> after = Map.of(
        "commentId", comment.id().toString(),
        "body", comment.body(),
        "authorId", actorId,
        "internal", comment.internal()
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
    if (!comment.internal()) {
      notifyWatchers(item, comment, actorId, correlationId);
    }
    return comment;
  }

  private void notifyWatchers(
      WorkItem item,
      WorkItemComment comment,
      String actorId,
      UUID correlationId
  ) {
    try {
      List<String> watchers = store.listWatchers(item.id());
      if (watchers == null || watchers.isEmpty()) {
        return;
      }
      for (String watcher : watchers) {
        if (watcher == null || watcher.isBlank() || watcher.equals(actorId)) {
          continue;
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("workItemId", item.id().toString());
        variables.put("number", item.number());
        variables.put("title", item.title());
        variables.put("commentId", comment.id().toString());
        variables.put("actorId", actorId);
        variables.put("bodyPreview", truncate(comment.body(), 160));
        notifications.send(new NotificationRequest(
            correlationId,
            "work-item.comment.watcher",
            watcher,
            "ru",
            variables,
            NotificationRequest.Channel.IN_APP
        ));
      }
    } catch (Exception ex) {
      log.warn("Watcher notification failed for comment on {}: {}", item.id(), ex.toString());
    }
  }

  private static String truncate(String body, int max) {
    if (body == null) {
      return "";
    }
    return body.length() <= max ? body : body.substring(0, max) + "…";
  }

  public record Command(String body, boolean internal) {}
}
