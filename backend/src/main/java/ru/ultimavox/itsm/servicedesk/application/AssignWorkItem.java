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

  public AssignWorkItem(
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
    WorkItem existing = store.requireById(id);
    if (!existing.isOpen()) {
      throw new IllegalStateException("Cannot assign a closed or cancelled work item");
    }

    boolean unassign = command.assigneeId() == null || command.assigneeId().isBlank();
    String assigneeId = unassign ? null : command.assigneeId().trim();
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    String teamId = command.teamId() != null ? command.teamId() : existing.teamId();
    WorkItem updated = existing.assign(assigneeId, teamId, now);
    store.update(updated);

    String action = unassign ? "work-item.unassigned" : "work-item.assigned";
    Map<String, Object> before = CreateWorkItem.snapshot(existing);
    Map<String, Object> after = CreateWorkItem.snapshot(updated);
    audit.append(new AuditTrail.Entry(
        actorId,
        action,
        "work-item",
        id.toString(),
        before,
        after,
        correlationId,
        now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(),
        action,
        1,
        now,
        correlationId,
        "work-item",
        id.toString(),
        after
    ));
    if (unassign) {
      notifyUnassigned(existing, updated, actorId, correlationId);
    } else {
      notifyAssigned(updated, actorId, correlationId);
      notifyWatchers(updated, actorId, correlationId);
    }
    return updated;
  }

  private void notifyUnassigned(WorkItem before, WorkItem after, String actorId, UUID correlationId) {
    String previous = before.assigneeId();
    if (previous == null || previous.isBlank() || previous.equals(actorId)) {
      return;
    }
    try {
      Map<String, Object> variables = baseAssignVars(after, actorId);
      variables.put("previousAssigneeId", previous);
      notifications.send(new NotificationRequest(
          correlationId,
          "work-item.unassigned",
          previous,
          "ru",
          variables,
          NotificationRequest.Channel.IN_APP
      ));
    } catch (Exception ex) {
      log.warn("Notification failed for work-item unassign {}: {}", after.id(), ex.toString());
    }
  }

  private void notifyAssigned(WorkItem item, String actorId, UUID correlationId) {
    try {
      Map<String, Object> variables = baseAssignVars(item, actorId);
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

  private void notifyWatchers(WorkItem item, String actorId, UUID correlationId) {
    try {
      java.util.List<String> watcherList = store.listWatchers(item.id());
      if (watcherList == null || watcherList.isEmpty()) {
        return;
      }
      for (String watcher : watcherList) {
        if (watcher == null || watcher.isBlank()) {
          continue;
        }
        if (watcher.equals(item.assigneeId()) || watcher.equals(actorId)) {
          continue;
        }
        Map<String, Object> variables = baseAssignVars(item, actorId);
        variables.put("watcherSubject", watcher);
        notifications.send(new NotificationRequest(
            correlationId,
            "work-item.assigned.watcher",
            watcher,
            "ru",
            variables,
            NotificationRequest.Channel.IN_APP
        ));
      }
    } catch (Exception ex) {
      log.warn("Watcher notification failed for work-item assign {}: {}", item.id(), ex.toString());
    }
  }

  private static Map<String, Object> baseAssignVars(WorkItem item, String actorId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("workItemId", item.id().toString());
    variables.put("number", item.number());
    variables.put("title", item.title());
    if (item.assigneeId() != null) {
      variables.put("assigneeId", item.assigneeId());
    }
    if (item.teamId() != null) {
      variables.put("teamId", item.teamId());
    }
    variables.put("actorId", actorId);
    return variables;
  }

  public record Command(String assigneeId, String teamId) {}
}
