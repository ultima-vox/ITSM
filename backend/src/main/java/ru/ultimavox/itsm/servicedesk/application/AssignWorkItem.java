package ru.ultimavox.itsm.servicedesk.application;

import java.time.Instant;
import java.util.HashMap;
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

@Service
public class AssignWorkItem {

  private static final Logger log = LoggerFactory.getLogger(AssignWorkItem.class);

  private final WorkItemStore store;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final NotificationService notifications;

  AssignWorkItem(
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
  public WorkItem assign(UUID id, Command command, String actorId) {
    if (command.assigneeId() == null || command.assigneeId().isBlank()) {
      throw new IllegalArgumentException("assigneeId is required");
    }

    WorkItem existing = store.requireById(id);
    if (!existing.isOpen()) {
      throw new IllegalStateException("Cannot assign a closed or cancelled work item");
    }

    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();
    String teamId = command.teamId() != null ? command.teamId() : existing.teamId();
    WorkItem updated = existing.assign(command.assigneeId().trim(), teamId, now);
    store.update(updated);

    Map<String, Object> before = CreateWorkItem.snapshot(existing);
    Map<String, Object> after = CreateWorkItem.snapshot(updated);
    audit.append(new AuditTrail.Entry(
        actorId,
        "work-item.assigned",
        "work-item",
        id.toString(),
        before,
        after,
        correlationId,
        now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(),
        "work-item.assigned",
        1,
        now,
        correlationId,
        "work-item",
        id.toString(),
        after
    ));
    notifyAssigned(updated, actorId, correlationId);
    return updated;
  }

  private void notifyAssigned(WorkItem item, String actorId, UUID correlationId) {
    try {
      Map<String, Object> variables = new HashMap<>();
      variables.put("workItemId", item.id().toString());
      variables.put("number", item.number());
      variables.put("title", item.title());
      variables.put("assigneeId", item.assigneeId());
      variables.put("teamId", item.teamId());
      variables.put("actorId", actorId);
      notifications.send(new NotificationRequest(
          correlationId,
          "work-item.assigned",
          item.assigneeId(),
          "ru",
          variables,
          NotificationRequest.Channel.IN_APP
      ));
    } catch (Exception ex) {
      log.warn("Notification failed for work-item assign {}: {}", item.id(), ex.toString());
    }
  }

  public record Command(String assigneeId, String teamId) {}
}
