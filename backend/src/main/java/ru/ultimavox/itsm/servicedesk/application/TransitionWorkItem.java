package ru.ultimavox.itsm.servicedesk.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.notification.NotificationRequest;
import ru.ultimavox.itsm.platform.notification.NotificationService;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.sla.SlaClockRepository;
import ru.ultimavox.itsm.platform.sla.SlaService;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.Transition;
import ru.ultimavox.itsm.platform.workflow.WorkflowEngine;
import ru.ultimavox.itsm.platform.workflow.WorkflowEngine.TransitionCommand;
import ru.ultimavox.itsm.platform.workflow.WorkflowTransitionException;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;

/**
 * Work-item state transitions.
 *
 * <p>Prefers the platform {@link WorkflowEngine} when a bean and an active
 * {@code work-item} definition exist and a matching transition is found for
 * the from→to pair. Otherwise falls back to the local aggregate state machine
 * so unit tests and environments without metadata remain functional.
 */
@Service
public class TransitionWorkItem {

  private static final Logger log = LoggerFactory.getLogger(TransitionWorkItem.class);
  static final String OBJECT_TYPE = "work-item";

  private final WorkItemStore store;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final ObjectProvider<WorkflowEngine> workflowEngine;
  private final NotificationService notifications;
  private final WorkItemSearchIndexer searchIndexer;
  private final SlaClockRepository slaClocks;
  private final SlaService sla;

  TransitionWorkItem(
      WorkItemStore store,
      AuditTrail audit,
      IntegrationEventOutbox outbox,
      ObjectProvider<WorkflowEngine> workflowEngine,
      NotificationService notifications,
      WorkItemSearchIndexer searchIndexer,
      SlaClockRepository slaClocks,
      SlaService sla
  ) {
    this.store = store;
    this.audit = audit;
    this.outbox = outbox;
    this.workflowEngine = workflowEngine;
    this.notifications = notifications;
    this.searchIndexer = searchIndexer;
    this.slaClocks = slaClocks;
    this.sla = sla;
  }

  @Transactional
  public WorkItem transition(UUID id, Command command, String actorId) {
    if (command.targetState() == null) {
      throw new IllegalArgumentException("targetState is required");
    }

    WorkItem existing = store.requireById(id);
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();

    Map<String, Object> fields = workflowFields(existing, command);
    boolean usedPlatformWorkflow = tryPlatformWorkflow(
        existing, command.targetState(), actorId, fields, correlationId
    );

    WorkItem updated = existing.transition(
        command.targetState(),
        command.resolutionCode(),
        command.resolutionNotes(),
        now
    );
    store.update(updated);

    if (updated.isTerminal()) {
      int stopped = slaClocks.achieveFor(id, actorId);
      if (stopped > 0) {
        log.debug("Stopped {} SLA clocks for resolved work item {}", stopped, id);
      }
    } else if (sla.isPauseable(id, updated.state().name())) {
      sla.pauseForState(id, updated.state().name(), actorId);
    } else {
      sla.resumeAll(id, actorId);
    }

    Map<String, Object> before = CreateWorkItem.snapshot(existing);
    Map<String, Object> after = CreateWorkItem.snapshot(updated);
    after.put("workflowEngine", usedPlatformWorkflow ? "platform" : "local");

    audit.append(new AuditTrail.Entry(
        actorId,
        "work-item.transitioned",
        OBJECT_TYPE,
        id.toString(),
        before,
        after,
        correlationId,
        now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(),
        "work-item.transitioned",
        1,
        now,
        correlationId,
        OBJECT_TYPE,
        id.toString(),
        after
    ));
    searchIndexer.index(updated);
    notifyTransitioned(existing, updated, actorId, correlationId);
    notifyWatchersTransitioned(existing, updated, actorId, correlationId);
    return updated;
  }

  private void notifyTransitioned(
      WorkItem before,
      WorkItem after,
      String actorId,
      UUID correlationId
  ) {
    String recipient = primaryRecipient(after);
    if (recipient == null) {
      return;
    }
    try {
      notifications.send(new NotificationRequest(
          correlationId,
          "work-item.transitioned",
          recipient,
          "ru",
          transitionVars(before, after, actorId),
          NotificationRequest.Channel.IN_APP
      ));
    } catch (Exception ex) {
      log.warn("Notification failed for work-item transition {}: {}", after.id(), ex.toString());
    }
  }

  private void notifyWatchersTransitioned(
      WorkItem before,
      WorkItem after,
      String actorId,
      UUID correlationId
  ) {
    try {
      java.util.List<String> watcherList = store.listWatchers(after.id());
      if (watcherList == null || watcherList.isEmpty()) {
        return;
      }
      String primary = primaryRecipient(after);
      for (String watcher : watcherList) {
        if (watcher == null || watcher.isBlank()) {
          continue;
        }
        if (watcher.equals(actorId) || watcher.equals(primary)) {
          continue;
        }
        Map<String, Object> variables = transitionVars(before, after, actorId);
        variables.put("watcherSubject", watcher);
        notifications.send(new NotificationRequest(
            correlationId,
            "work-item.transitioned.watcher",
            watcher,
            "ru",
            variables,
            NotificationRequest.Channel.IN_APP
        ));
      }
    } catch (Exception ex) {
      log.warn("Watcher notification failed for work-item transition {}: {}", after.id(), ex.toString());
    }
  }

  private static Map<String, Object> transitionVars(
      WorkItem before,
      WorkItem after,
      String actorId
  ) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("workItemId", after.id().toString());
    variables.put("number", after.number());
    variables.put("title", after.title());
    variables.put("fromState", before.state().name());
    variables.put("toState", after.state().name());
    variables.put("actorId", actorId);
    return variables;
  }

  private static String primaryRecipient(WorkItem item) {
    if (item.assigneeId() != null && !item.assigneeId().isBlank()) {
      return item.assigneeId();
    }
    if (item.requesterId() != null && !item.requesterId().isBlank()) {
      return item.requesterId();
    }
    return null;
  }

  /**
   * Attempts platform workflow. Returns true when the engine applied a matching transition.
   * Returns false when the engine is absent or no definition exists. An active definition is
   * authoritative and rejects missing transition edges.
   * Re-throws {@link WorkflowTransitionException} when a matching transition is rejected
   * (permissions / required fields) so policy is not bypassed.
   */
  private boolean tryPlatformWorkflow(
      WorkItem existing,
      State targetState,
      String actorId,
      Map<String, Object> fields,
      UUID correlationId
  ) {
    WorkflowEngine engine = workflowEngine.getIfAvailable();
    if (engine == null) {
      return false;
    }

    Optional<WorkflowDefinition> definition = engine.loadDefinition(OBJECT_TYPE);
    if (definition.isEmpty()) {
      log.debug("No active workflow definition for {}; using local state machine", OBJECT_TYPE);
      return false;
    }

    Optional<Transition> match = definition.get().transitionsFrom(existing.state().name()).stream()
        .filter(t -> t.to().equals(targetState.name()))
        .findFirst();

    if (match.isEmpty()) {
      // Configured workflow owns allowed edges; aggregate still enforces safety invariants.
      throw new WorkflowTransitionException(
          "Active workflow '%s' has no transition %s -> %s"
              .formatted(OBJECT_TYPE, existing.state(), targetState));
    }

    try {
      engine.applyTransition(new TransitionCommand(
          actorId,
          OBJECT_TYPE,
          existing.id().toString(),
          match.get().key(),
          fields,
          correlationId
      ));
      return true;
    } catch (WorkflowTransitionException ex) {
      // Matching transition was found but rejected — do not silently bypass.
      throw ex;
    }
  }

  private static Map<String, Object> workflowFields(WorkItem item, Command command) {
    Map<String, Object> fields = new HashMap<>();
    if (item.assigneeId() != null) {
      fields.put("assignee_id", item.assigneeId());
    }
    if (command.resolutionCode() != null) {
      fields.put("resolution_code", command.resolutionCode());
    }
    if (command.resolutionNotes() != null) {
      fields.put("resolution_notes", command.resolutionNotes());
    }
    return fields;
  }

  public record Command(State targetState, String resolutionCode, String resolutionNotes) {}
}
