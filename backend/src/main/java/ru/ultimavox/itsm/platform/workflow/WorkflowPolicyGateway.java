package ru.ultimavox.itsm.platform.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.Transition;
import ru.ultimavox.itsm.platform.workflow.WorkflowEngine.TransitionCommand;

/** Public workflow policy contract used by business modules. */
@Service
public class WorkflowPolicyGateway {
  private final WorkflowEngine engine;

  public WorkflowPolicyGateway(WorkflowEngine engine) {
    this.engine = engine;
  }

  public boolean startIfDefined(String objectType, String objectId) {
    if (engine.loadDefinition(objectType).isEmpty()) {
      return false;
    }
    engine.ensureStarted(objectType, objectId);
    return true;
  }

  public List<String> listAvailableTargets(String objectType, String currentState) {
    Optional<WorkflowDefinition> def = engine.loadDefinition(objectType);
    if (def.isEmpty()) return List.of();
    return def.get().transitionsFrom(currentState).stream()
        .map(Transition::to)
        .toList();
  }

  public boolean enforceByTarget(
      String subject,
      String objectType,
      String objectId,
      String currentState,
      String targetState,
      Map<String, Object> fields,
      UUID correlationId
  ) {
    Optional<WorkflowDefinition> configured = engine.loadDefinition(objectType);
    if (configured.isEmpty()) {
      return false;
    }
    Transition transition = configured.get().transitionsFrom(currentState).stream()
        .filter(candidate -> candidate.to().equals(targetState))
        .findFirst()
        .orElseThrow(() -> new WorkflowTransitionException(
            "Active workflow '%s' has no transition %s -> %s"
                .formatted(objectType, currentState, targetState)));
    engine.applyTransition(new TransitionCommand(
        subject,
        objectType,
        objectId,
        transition.key(),
        fields == null ? Map.of() : Map.copyOf(fields),
        correlationId
    ));
    return true;
  }
}
