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
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
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

  TransitionWorkItem(
      WorkItemStore store,
      AuditTrail audit,
      IntegrationEventOutbox outbox,
      ObjectProvider<WorkflowEngine> workflowEngine
  ) {
    this.store = store;
    this.audit = audit;
    this.outbox = outbox;
    this.workflowEngine = workflowEngine;
  }

  @Transactional
  public WorkItem transition(UUID id, Command command, String actorId) {
    if (command.targetState() == null) {
      throw new IllegalArgumentException("targetState is required");
    }

    WorkItem existing = store.requireById(id);
    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();

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
    return updated;
  }

  /**
   * Attempts platform workflow. Returns true when the engine applied a matching transition.
   * Returns false when the engine is absent, no definition exists, or no matching transition
   * key is defined (local aggregate remains authoritative).
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
      // Definition exists but does not model this edge — keep local fallback for partial metadata.
      log.debug(
          "No workflow transition {} -> {} for {}; using local state machine",
          existing.state(), targetState, OBJECT_TYPE
      );
      return false;
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
