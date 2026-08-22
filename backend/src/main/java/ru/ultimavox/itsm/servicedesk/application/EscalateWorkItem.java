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
import ru.ultimavox.itsm.platform.oncall.OnCallDirectory;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;

@Service
public class EscalateWorkItem {

  private static final Logger log = LoggerFactory.getLogger(EscalateWorkItem.class);

  private final WorkItemStore store;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final NotificationService notifications;
  private final WorkItemSearchIndexer searchIndexer;
  private final OnCallDirectory onCall;

  /** Escalation policy consulted when a work item is escalated; absent policy means nobody extra is paged. */
  private static final String ESCALATION_POLICY_KEY = "work-item.escalation";

  public EscalateWorkItem(
      WorkItemStore store,
      AuditTrail audit,
      IntegrationEventOutbox outbox,
      NotificationService notifications,
      WorkItemSearchIndexer searchIndexer,
      OnCallDirectory onCall
  ) {
    this.store = store;
    this.audit = audit;
    this.outbox = outbox;
    this.notifications = notifications;
    this.searchIndexer = searchIndexer;
    this.onCall = onCall;
  }

  @Transactional
  public WorkItem escalate(UUID id, String actorId) {
    WorkItem existing = store.requireById(id);
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();

    WorkItem updated = existing.escalate(now);
    if (updated.state() == State.NEW) {
      updated = updated.transition(State.IN_PROGRESS, null, null, now);
    }
    store.update(updated);

    Map<String, Object> before = CreateWorkItem.snapshot(existing);
    Map<String, Object> after = CreateWorkItem.snapshot(updated);
    after.put("escalated", true);
    audit.append(new AuditTrail.Entry(
        actorId,
        "work-item.escalated",
        "work-item",
        id.toString(),
        before,
        after,
        correlationId,
        now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(),
        "work-item.escalated",
        1,
        now,
        correlationId,
        "work-item",
        id.toString(),
        after
    ));
    searchIndexer.index(updated);
    notifyEscalated(updated, actorId, correlationId);
    notifyOnCall(updated, actorId, correlationId, now);
    return updated;
  }

  /**
   * Pages the responders of the {@code work-item.escalation} policy, so an escalation reaches
   * whoever is actually on call rather than stopping at the current assignee.
   */
  private void notifyOnCall(WorkItem item, String actorId, UUID correlationId, Instant at) {
    try {
      for (OnCallDirectory.Responder responder : onCall.escalationChain(ESCALATION_POLICY_KEY, at)) {
        if (responder.subject() == null || responder.subject().isBlank()
            || responder.subject().equals(item.assigneeId())) {
          continue;
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("workItemId", item.id().toString());
        variables.put("number", item.number());
        variables.put("title", item.title());
        variables.put("priority", item.priority().name());
        variables.put("actorId", actorId);
        variables.put("escalationStep", responder.stepOrder());
        variables.put("delayMinutes", responder.delayMinutes());
        notifications.send(new NotificationRequest(
            correlationId,
            "work-item.escalated",
            responder.subject(),
            "ru",
            variables,
            NotificationRequest.Channel.IN_APP
        ));
      }
    } catch (Exception ex) {
      log.warn("On-call notification failed for escalate {}: {}", item.id(), ex.toString());
    }
  }

  private void notifyEscalated(WorkItem item, String actorId, UUID correlationId) {
    try {
      String recipient = item.assigneeId() != null && !item.assigneeId().isBlank()
          ? item.assigneeId()
          : item.requesterId();
      if (recipient == null || recipient.isBlank()) {
        return;
      }
      Map<String, Object> variables = new HashMap<>();
      variables.put("workItemId", item.id().toString());
      variables.put("number", item.number());
      variables.put("title", item.title());
      variables.put("priority", item.priority().name());
      variables.put("actorId", actorId);
      notifications.send(new NotificationRequest(
          correlationId,
          "work-item.escalated",
          recipient,
          "ru",
          variables,
          NotificationRequest.Channel.IN_APP
      ));
    } catch (Exception ex) {
      log.warn("Notification failed for escalate {}: {}", item.id(), ex.toString());
    }
  }
}
