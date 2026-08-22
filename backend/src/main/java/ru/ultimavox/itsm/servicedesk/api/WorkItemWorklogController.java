package ru.ultimavox.itsm.servicedesk.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.servicedesk.application.WorkItemWorklogService;

@RestController
@RequestMapping("/api/v1/work-items/{id}/worklogs")
@Tag(name = "Service Desk")
class WorkItemWorklogController {
  private final WorkItemWorklogService worklogs;
  private final AccessControl access;

  WorkItemWorklogController(WorkItemWorklogService worklogs, AccessControl access) {
    this.worklogs = worklogs;
    this.access = access;
  }

  @GetMapping
  @Operation(summary = "List the time logged against a work item")
  WorkItemWorklogService.Summary list(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "work-item.read", "work-item", id.toString());
    return worklogs.list(id);
  }

  @PostMapping
  @Operation(summary = "Log time spent on a work item")
  ResponseEntity<WorkItemWorklogService.Entry> log(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody LogRequest body
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.worklog", "work-item", id.toString());
    WorkItemWorklogService.Entry created = worklogs.log(
        id,
        new WorkItemWorklogService.LogCommand(
            body.minutes(), body.startedAt(), body.note(), Boolean.TRUE.equals(body.billable())),
        actor);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PatchMapping("/{worklogId}")
  @Operation(summary = "Correct a logged entry; only its author, or a worklog manager")
  WorkItemWorklogService.Entry update(
      Authentication authentication,
      @PathVariable UUID id,
      @PathVariable UUID worklogId,
      @Valid @RequestBody UpdateRequest body
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.worklog", "work-item", id.toString());
    return worklogs.update(
        id,
        worklogId,
        new WorkItemWorklogService.UpdateCommand(
            body.minutes(), body.startedAt(), body.note(), body.billable()),
        actor,
        canManageAnyAuthor(actor, id));
  }

  @DeleteMapping("/{worklogId}")
  @Operation(summary = "Delete a logged entry; only its author, or a worklog manager")
  ResponseEntity<Void> delete(
      Authentication authentication,
      @PathVariable UUID id,
      @PathVariable UUID worklogId
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.worklog", "work-item", id.toString());
    worklogs.delete(id, worklogId, actor, canManageAnyAuthor(actor, id));
    return ResponseEntity.noContent().build();
  }

  private boolean canManageAnyAuthor(String actor, UUID workItemId) {
    return access.isAllowed(actor, "work-item.worklog.manage", "work-item", workItemId.toString());
  }

  record LogRequest(
      @Min(1) @Max(1440) int minutes,
      @NotNull Instant startedAt,
      @Size(max = 4000) String note,
      Boolean billable
  ) {}

  record UpdateRequest(
      @Min(1) @Max(1440) Integer minutes,
      Instant startedAt,
      @Size(max = 4000) String note,
      Boolean billable
  ) {}
}
