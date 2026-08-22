package ru.ultimavox.itsm.releasemanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.workflow.WorkflowPolicyGateway;
import ru.ultimavox.itsm.releasemanagement.application.ReleaseCommands;
import ru.ultimavox.itsm.releasemanagement.application.ReleaseContentService;
import ru.ultimavox.itsm.releasemanagement.application.ReleaseQuery;
import ru.ultimavox.itsm.releasemanagement.domain.Release;

@RestController
@RequestMapping("/api/v1/releases")
@Tag(name = "Release Management")
class ReleaseController {
  private final ReleaseQuery query;
  private final ReleaseCommands commands;
  private final ReleaseContentService content;
  private final AccessControl access;
  private final WorkflowPolicyGateway workflowPolicy;

  ReleaseController(
      ReleaseQuery query,
      ReleaseCommands commands,
      ReleaseContentService content,
      AccessControl access,
      WorkflowPolicyGateway workflowPolicy
  ) {
    this.query = query;
    this.commands = commands;
    this.content = content;
    this.access = access;
    this.workflowPolicy = workflowPolicy;
  }

  @GetMapping
  @Operation(summary = "List releases")
  ReleaseListResponse list(
      Authentication authentication,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) @Size(max = 2000) String q,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "50") int size
  ) {
    access.require(authentication.getName(), "release.read", "release", null);
    int safeSize = Math.min(Math.max(size, 1), 200);
    int safePage = Math.max(page, 0);
    List<Release> all = query.list(status, type, q);
    int total = all.size();
    int from = safePage * safeSize;
    int to = Math.min(from + safeSize, total);
    List<Release> items = from < total ? all.subList(from, to) : List.of();
    return new ReleaseListResponse(items, total, safePage, safeSize);
  }

  @GetMapping("/conflicts")
  @Operation(summary = "Detect release window conflicts")
  List<Release> conflicts(
      Authentication authentication,
      @RequestParam Instant start,
      @RequestParam Instant end,
      @RequestParam(required = false) UUID excludeId
  ) {
    access.require(authentication.getName(), "release.read", "release", null);
    if (start == null || end == null || !end.isAfter(start)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start must be before end");
    }
    return query.findScheduleConflicts(start, end, excludeId);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get release by id")
  Release get(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "release.read", "release", id.toString());
    return query.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found"));
  }

  @GetMapping("/{id}/transitions")
  @Operation(summary = "List available target states for a release")
  List<String> transitions(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "release.read", "release", id.toString());
    Release release = query.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found"));
    return workflowPolicy.listAvailableTargets("release", release.status().name());
  }

  @GetMapping("/{id}/changes")
  @Operation(summary = "List the changes that ship with a release")
  ReleaseContentResponse listContent(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "release.read", "release", id.toString());
    return contentResponse(execute(() -> content.content(id)));
  }

  @PostMapping
  @Operation(summary = "Create a release (PLANNING)")
  ResponseEntity<Release> create(Authentication authentication, @Valid @RequestBody CreateRequest body) {
    access.require(authentication.getName(), "release.write", "release", null);
    Release created = execute(() -> commands.create(
        new ReleaseCommands.CreateCommand(
            body.name(), body.type(), body.description(), body.deploymentPlan(),
            body.rollbackPlan(), body.releaseManager(), body.plannedStart(), body.plannedEnd()),
        authentication.getName()));
    return ResponseEntity.created(URI.create("/api/v1/releases/" + created.id())).body(created);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update a release with optimistic locking")
  Release update(Authentication authentication, @PathVariable UUID id,
                 @Valid @RequestBody UpdateRequest body) {
    access.require(authentication.getName(), "release.write", "release", id.toString());
    return execute(() -> commands.update(id, new ReleaseCommands.UpdateCommand(
        body.expectedVersion(), body.name(), body.type(), body.description(), body.deploymentPlan(),
        body.rollbackPlan(), body.testSummary(), body.releaseManager(),
        body.plannedStart(), body.plannedEnd()), authentication.getName()));
  }

  @PostMapping("/{id}/transitions")
  @Operation(summary = "Transition the release lifecycle through its build and deployment gates")
  Release transition(Authentication authentication, @PathVariable UUID id,
                     @Valid @RequestBody TransitionRequest body) {
    access.require(authentication.getName(), "release.write", "release", id.toString());
    return execute(() -> commands.transition(
        id, body.target(), body.expectedVersion(), authentication.getName()));
  }

  @PostMapping("/{id}/go-decision")
  @Operation(summary = "Record the go / no-go decision for a release under review")
  Release goDecision(Authentication authentication, @PathVariable UUID id,
                     @Valid @RequestBody GoDecisionRequest body) {
    access.require(authentication.getName(), "release.approve", "release", id.toString());
    return execute(() -> commands.recordGoDecision(
        id, body.decision(), body.notes(), body.expectedVersion(), authentication.getName()));
  }

  @PostMapping("/{id}/changes")
  @Operation(summary = "Link changes to a release")
  ReleaseContentResponse linkContent(Authentication authentication, @PathVariable UUID id,
                                     @Valid @RequestBody LinkChangesRequest body) {
    access.require(authentication.getName(), "release.write", "release", id.toString());
    return contentResponse(execute(() -> content.link(id, body.changeIds(), authentication.getName())));
  }

  @DeleteMapping("/{id}/changes/{changeId}")
  @Operation(summary = "Remove a change from a release")
  ReleaseContentResponse unlinkContent(Authentication authentication, @PathVariable UUID id,
                                       @PathVariable UUID changeId) {
    access.require(authentication.getName(), "release.write", "release", id.toString());
    return contentResponse(execute(() -> content.unlink(id, changeId, authentication.getName())));
  }

  private static ReleaseContentResponse contentResponse(List<ReleaseContentService.ContentEntry> entries) {
    long blocking = entries.stream().filter(entry -> !entry.deployable()).count();
    return new ReleaseContentResponse(entries, entries.size(), blocking, blocking == 0);
  }

  private static <T> T execute(java.util.function.Supplier<T> action) {
    try {
      return action.get();
    } catch (OptimisticLockingFailureException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalArgumentException ex) {
      HttpStatus status = ex.getMessage() != null && ex.getMessage().startsWith("Release not found")
          ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
      throw new ResponseStatusException(status, ex.getMessage());
    }
  }

  record CreateRequest(
      @NotBlank @Size(max = 240) String name,
      @NotNull Release.Type type,
      @Size(max = 8000) String description,
      @Size(max = 20000) String deploymentPlan,
      @Size(max = 20000) String rollbackPlan,
      @Size(max = 128) String releaseManager,
      Instant plannedStart,
      Instant plannedEnd
  ) {}

  record UpdateRequest(
      long expectedVersion,
      @Size(max = 240) String name,
      Release.Type type,
      @Size(max = 8000) String description,
      @Size(max = 20000) String deploymentPlan,
      @Size(max = 20000) String rollbackPlan,
      @Size(max = 20000) String testSummary,
      @Size(max = 128) String releaseManager,
      Instant plannedStart,
      Instant plannedEnd
  ) {}

  record TransitionRequest(@NotNull Release.Status target, Long expectedVersion) {}

  record GoDecisionRequest(
      @NotNull Release.GoDecision decision,
      @Size(max = 8000) String notes,
      Long expectedVersion
  ) {}

  record LinkChangesRequest(
      @NotNull @Size(min = 1, max = 100) List<@NotNull UUID> changeIds
  ) {}

  record ReleaseContentResponse(
      List<ReleaseContentService.ContentEntry> items,
      int total,
      long blocking,
      boolean deployable
  ) {}

  record ReleaseListResponse(List<Release> items, int total, int page, int size) {}
}
