package ru.ultimavox.itsm.platform.workflow.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.workflow.WorkflowTimerService;

@RestController
@RequestMapping("/api/v1/workflow/instances/{objectType}/{objectId}/timers")
@Tag(name = "Platform — Workflow Timers")
class WorkflowTimerController {
  private final WorkflowTimerService timers;
  private final AccessControl access;

  WorkflowTimerController(WorkflowTimerService timers, AccessControl access) {
    this.timers = timers;
    this.access = access;
  }

  @GetMapping
  @Operation(summary = "List durable timer history for a workflow instance")
  List<TimerResponse> list(Authentication authentication, @PathVariable String objectType,
                   @PathVariable String objectId) {
    access.require(authentication.getName(), "workflow.timer.read", "workflow_instance",
        objectType + "/" + objectId);
    return timers.list(objectType, objectId).stream().map(timer -> new TimerResponse(
        timer.id(), timer.transitionKey(), timer.definitionVersion(), timer.sourceInstanceVersion(),
        timer.dueAt(), timer.status(), timer.attempts(), timer.maxAttempts(), timer.lastError())).toList();
  }

  record TimerResponse(UUID id, String transitionKey, int definitionVersion,
      int sourceInstanceVersion, Instant dueAt, String status, int attempts,
      int maxAttempts, String lastError) {}
}
