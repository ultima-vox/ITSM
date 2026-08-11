package ru.ultimavox.itsm.servicedesk.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.servicedesk.application.WorkItemTemplateService;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

@RestController
@RequestMapping("/api/v1/work-item-templates")
@Tag(name = "Service Desk — Templates")
class WorkItemTemplateController {
  private final WorkItemTemplateService templates;
  private final AccessControl access;

  WorkItemTemplateController(WorkItemTemplateService templates, AccessControl access) {
    this.templates = templates;
    this.access = access;
  }

  @GetMapping
  @Operation(summary = "List available work item templates")
  List<WorkItemTemplateService.Template> list(
      @RequestParam(defaultValue = "false") boolean includeInactive,
      Authentication authentication
  ) {
    access.require(authentication.getName(), "work-item.read", "work-item-template", null);
    if (includeInactive) {
      access.require(authentication.getName(), "work-item.template.manage", "work-item-template", null);
    }
    return templates.list(includeInactive);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a reusable work item template")
  WorkItemTemplateService.Template create(
      @Valid @RequestBody CreateRequest request, Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.template.manage", "work-item-template", null);
    return templates.create(new WorkItemTemplateService.Command(
        request.name(), request.type(), request.title(), request.description(), request.service(),
        request.impact(), request.urgency(), request.teamId()), actor);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Archive a work item template using optimistic locking")
  void archive(
      @PathVariable UUID id,
      @RequestParam @PositiveOrZero long version,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.template.manage", "work-item-template", id.toString());
    templates.archive(id, version, actor);
  }

  @PutMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Update a work item template using optimistic locking")
  void update(
      @PathVariable UUID id,
      @RequestParam @PositiveOrZero long version,
      @Valid @RequestBody CreateRequest request,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.template.manage", "work-item-template", id.toString());
    templates.update(id, version, new WorkItemTemplateService.Command(
        request.name(), request.type(), request.title(), request.description(), request.service(),
        request.impact(), request.urgency(), request.teamId()), actor);
  }

  record CreateRequest(
      @NotBlank @Size(max = 160) String name,
      @NotNull WorkItem.Type type,
      @NotBlank @Size(max = 240) String title,
      @NotBlank @Size(max = 12000) String description,
      @NotBlank @Size(max = 100) String service,
      @NotNull WorkItem.Impact impact,
      @NotNull WorkItem.Urgency urgency,
      @Size(max = 128) String teamId
  ) {}
}
