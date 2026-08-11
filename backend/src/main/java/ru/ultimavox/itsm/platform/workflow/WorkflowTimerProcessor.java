package ru.ultimavox.itsm.platform.workflow;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.workflow.WorkflowTimerService.Timer;

/** Executes one claimed timer under its persisted tenant scope. */
@Service
class WorkflowTimerProcessor {
  private final WorkflowEngine engine;

  WorkflowTimerProcessor(WorkflowEngine engine) {
    this.engine = engine;
  }

  Result execute(Timer timer) {
    return OrganizationContext.runAs(timer.orgId(), () -> {
      WorkflowInstance current = engine.findInstance(timer.objectType(), timer.objectId()).orElse(null);
      if (current == null || !current.id().equals(timer.workflowInstanceId())
          || current.version() != timer.sourceInstanceVersion()
          || current.definitionVersion() != timer.definitionVersion()) {
        return Result.STALE;
      }
      engine.applyTransition(new WorkflowEngine.TransitionCommand(
          "system:workflow-timer", timer.objectType(), timer.objectId(), timer.transitionKey(),
          Map.of(), UUID.randomUUID()));
      return Result.COMPLETED;
    });
  }

  enum Result { COMPLETED, STALE }
}
