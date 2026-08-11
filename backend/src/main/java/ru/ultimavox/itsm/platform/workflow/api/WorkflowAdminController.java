package ru.ultimavox.itsm.platform.workflow.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinitionRepository;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinitionRepository.WorkflowDefinitionView;
import ru.ultimavox.itsm.platform.workflow.WorkflowEngine;
import ru.ultimavox.itsm.platform.workflow.WorkflowInstance;
import ru.ultimavox.itsm.platform.workflow.WorkflowTransitionException;

@RestController
@RequestMapping("/api/v1/workflow")
@Tag(name = "Platform — Workflow")
class WorkflowAdminController {

  private final WorkflowDefinitionRepository definitions;
  private final AccessControl access;
  private final WorkflowEngine engine;

  WorkflowAdminController(WorkflowDefinitionRepository definitions, AccessControl access, WorkflowEngine engine) {
    this.definitions = definitions;
    this.access = access;
    this.engine = engine;
  }

  @GetMapping("/definitions")
  @Operation(summary = "List workflow definition versions (admin read)")
  List<DefinitionResponse> listDefinitions(Authentication authentication) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "workflow.read", "workflow_definition", null);
    return definitions.listAll().stream().map(DefinitionResponse::from).toList();
  }

  @PatchMapping("/definitions/{id}")
  @Operation(summary = "Activate or deactivate a workflow version for current organization")
  DefinitionResponse setActive(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody SetActiveRequest body
  ) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "workflow.write", "workflow_definition", id.toString());
    return definitions.setActive(id, body.active())
        .map(DefinitionResponse::from)
        .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Workflow definition not found"));
  }

  record SetActiveRequest(boolean active) {}

  @GetMapping("/instances/{objectType}/{objectId}")
  @Operation(summary = "Get workflow instance state and pinned definition version")
  WorkflowInstance getInstance(
      Authentication authentication, @PathVariable String objectType, @PathVariable String objectId) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "workflow.read", "workflow_instance", objectType + "/" + objectId);
    return engine.findInstance(objectType, objectId)
        .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Workflow instance not found"));
  }

  @PostMapping("/instances/{objectType}/{objectId}/migrations")
  @Operation(summary = "Migrate an active workflow instance to a compatible definition version")
  WorkflowInstance migrateInstance(
      Authentication authentication,
      @PathVariable String objectType,
      @PathVariable String objectId,
      @RequestBody MigrateInstanceRequest body
  ) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "workflow.write", "workflow_instance", objectType + "/" + objectId);
    try {
      return engine.migrateInstance(new WorkflowEngine.MigrationCommand(
          actor, objectType, objectId, body.targetDefinitionVersion(), body.expectedVersion(), null));
    } catch (WorkflowTransitionException ex) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  record MigrateInstanceRequest(int targetDefinitionVersion, int expectedVersion) {}

  record DefinitionResponse(
      UUID id,
      String objectKey,
      int version,
      boolean active,
      String initialState,
      List<String> states,
      List<TransitionResponse> transitions
  ) {
    static DefinitionResponse from(WorkflowDefinitionView view) {
      WorkflowDefinition d = view.definition();
      return new DefinitionResponse(
          d.id(),
          d.objectKey(),
          d.version(),
          view.active(),
          d.initialState(),
          List.copyOf(d.states()),
          d.transitions().stream()
              .map(t -> new TransitionResponse(
                  t.key(),
                  t.from(),
                  t.to(),
                  List.copyOf(t.requiredPermissions()),
                  List.copyOf(t.requiredFields()),
                  t.conditions().stream().map(c -> new ConditionResponse(
                      c.field(), c.operator().name(), c.value())).toList(),
                  t.approval() == null ? null : new ApprovalResponse(
                      t.approval().mode().name(), List.copyOf(t.approval().voterRoles()), t.approval().quorum()),
                  t.timer() == null ? null : new TimerResponse(
                      t.timer().delaySeconds(), t.timer().maxAttempts())
              ))
              .toList()
      );
    }
  }

  record TransitionResponse(
      String key,
      String from,
      String to,
      List<String> requiredPermissions,
      List<String> requiredFields,
      List<ConditionResponse> conditions,
      ApprovalResponse approval,
      TimerResponse timer
  ) {}

  record ApprovalResponse(String mode, List<String> voterRoles, Integer quorum) {}
  record ConditionResponse(String field, String operator, Object value) {}
  record TimerResponse(long delaySeconds, int maxAttempts) {}
}
