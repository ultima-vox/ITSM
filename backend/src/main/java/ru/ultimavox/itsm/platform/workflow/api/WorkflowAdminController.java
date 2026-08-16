package ru.ultimavox.itsm.platform.workflow.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinitionRepository;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinitionRepository.WorkflowDefinitionView;

@RestController
@RequestMapping("/api/v1/workflow")
@Tag(name = "Platform — Workflow")
class WorkflowAdminController {

  private final WorkflowDefinitionRepository definitions;
  private final AccessControl access;

  WorkflowAdminController(WorkflowDefinitionRepository definitions, AccessControl access) {
    this.definitions = definitions;
    this.access = access;
  }

  @GetMapping("/definitions")
  @Operation(summary = "List workflow definition versions (admin read)")
  List<DefinitionResponse> listDefinitions(Authentication authentication) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "workflow.read", "workflow_definition", null);
    return definitions.listAll().stream().map(DefinitionResponse::from).toList();
  }

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
                  List.copyOf(t.requiredFields())
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
      List<String> requiredFields
  ) {}
}
