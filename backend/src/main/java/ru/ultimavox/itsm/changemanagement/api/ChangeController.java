package ru.ultimavox.itsm.changemanagement.api;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.changemanagement.application.CabVoteService;
import ru.ultimavox.itsm.changemanagement.application.ChangeCommands;
import ru.ultimavox.itsm.changemanagement.application.ChangeQuery;
import ru.ultimavox.itsm.changemanagement.domain.Change;
import ru.ultimavox.itsm.platform.authorization.AccessControl;

@RestController
@RequestMapping("/api/v1/changes")
@Tag(name = "Change Management")
class ChangeController {
  private final ChangeQuery query;
  private final ChangeCommands commands;
  private final CabVoteService cabVotes;
  private final AccessControl access;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  ChangeController(
      ChangeQuery query,
      ChangeCommands commands,
      CabVoteService cabVotes,
      AccessControl access,
      org.springframework.jdbc.core.JdbcTemplate jdbc
  ) {
    this.query = query;
    this.commands = commands;
    this.cabVotes = cabVotes;
    this.access = access;
    this.jdbc = jdbc;
  }

  @GetMapping
  @Operation(summary = "List changes")
  ChangeListResponse list(Authentication authentication,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String q,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "50") int size) {
    access.require(authentication.getName(), "change.read", "change", null);
    int safeSize = Math.min(Math.max(size, 1), 200);
    int safePage = Math.max(page, 0);
    List<Change> all = query.list(status, q);
    int total = all.size();
    int from = safePage * safeSize;
    int to = Math.min(from + safeSize, total);
    List<Change> items = from < total ? all.subList(from, to) : List.of();
    return new ChangeListResponse(items, total, safePage, safeSize);
  }

  @GetMapping("/conflicts")
  @Operation(summary = "Detect schedule conflicts for a planned window")
  List<Change> conflicts(
      Authentication authentication,
      @RequestParam Instant start,
      @RequestParam Instant end,
      @RequestParam(required = false) UUID excludeId
  ) {
    access.require(authentication.getName(), "change.read", "change", null);
    if (start == null || end == null || !end.isAfter(start)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start must be before end");
    }
    return query.findScheduleConflicts(start, end, excludeId);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get change by id")
  Change get(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "change.read", "change", id.toString());
    return query.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change not found"));
  }

  @GetMapping("/{id}/conflicts")
  @Operation(summary = "Schedule conflicts for an existing change window")
  List<Change> conflictsFor(
      Authentication authentication,
      @PathVariable UUID id
  ) {
    access.require(authentication.getName(), "change.read", "change", id.toString());
    Change change = query.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change not found"));
    if (change.plannedStart() == null || change.plannedEnd() == null) {
      return List.of();
    }
    return query.findScheduleConflicts(change.plannedStart(), change.plannedEnd(), id);
  }

  @PostMapping
  @Operation(summary = "Create a change request (DRAFT)")
  ResponseEntity<Change> create(Authentication authentication, @Valid @RequestBody CreateRequest body) {
    access.require(authentication.getName(), "change.write", "change", null);
    Change created = commands.create(
        new ChangeCommands.CreateCommand(
            body.type(),
            body.risk(),
            body.title(),
            body.plannedStart(),
            body.plannedEnd(),
            body.implementationPlan(),
            body.rollbackPlan(),
            body.testPlan(),
            body.businessJustification(),
            body.cabNotes(),
            body.cabRiskLevel(),
            body.impact()
        ),
        authentication.getName()
    );
    return ResponseEntity.created(URI.create("/api/v1/changes/" + created.id())).body(created);
  }

  @PostMapping("/{id}/transitions")
  @Operation(summary = "Transition change lifecycle; CAB risk fields accepted during review")
  Change transition(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody TransitionRequest body
  ) {
    access.require(authentication.getName(), "change.write", "change", id.toString());
    try {
      return commands.transition(
          id, body.target(), body.cabNotes(), body.cabRiskLevel(), body.expectedVersion(), authentication.getName()
      );
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @org.springframework.web.bind.annotation.PatchMapping("/{id}")
  @Operation(summary = "Update change plans and CAB assessment with optimistic locking")
  Change update(Authentication authentication, @PathVariable UUID id,
                @Valid @RequestBody UpdateRequest body) {
    access.require(authentication.getName(), "change.write", "change", id.toString());
    try {
      return commands.update(id, new ChangeCommands.UpdateCommand(
          body.expectedVersion(), body.plannedStart(), body.plannedEnd(), body.implementationPlan(),
          body.rollbackPlan(), body.testPlan(), body.businessJustification(), body.cabNotes(),
          body.cabRiskLevel(), body.impact()),
          authentication.getName());
    } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalArgumentException ex) {
      HttpStatus status = ex.getMessage() != null && ex.getMessage().startsWith("Change not found")
          ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
      throw new ResponseStatusException(status, ex.getMessage());
    }
  }

  @PostMapping("/bulk/transitions")
  @Operation(summary = "Transition up to 100 changes with explicit per-item results")
  BulkTransitionResponse bulkTransition(Authentication authentication,
                                         @Valid @RequestBody BulkTransitionRequest body) {
    String actor = authentication.getName();
    List<BulkTransitionResult> results = body.ids().stream().map(id -> {
      access.require(actor, "change.write", "change", id.toString());
      try {
        Change changed = commands.transition(id, body.target(), null, null, null, actor);
        return new BulkTransitionResult(id, true, changed.status().name(), null);
      } catch (IllegalArgumentException ex) {
        return new BulkTransitionResult(id, false, null, "NOT_FOUND");
      } catch (IllegalStateException ex) {
        return new BulkTransitionResult(id, false, null, "INVALID_TRANSITION");
      }
    }).toList();
    return new BulkTransitionResponse(results.stream().filter(BulkTransitionResult::success).count(), results);
  }

  @GetMapping("/{id}/votes")
  @Operation(summary = "List CAB votes for a change")
  CabVotesResponse listVotes(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "change.read", "change", id.toString());
    try {
      List<CabVoteService.CabVote> votes = cabVotes.listVotes(id);
      return new CabVotesResponse(
          votes,
          cabVotes.countApproves(id),
          CabVoteService.QUORUM_APPROVES
      );
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    }
  }

  @PostMapping("/{id}/votes")
  @Operation(summary = "Cast CAB member vote (APPROVE or REJECT)")
  CabVoteService.CabVote castVote(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody CabVoteRequest body
  ) {
    access.require(authentication.getName(), "change.approve", "change", id.toString());
    try {
      return cabVotes.castVote(id, body.decision(), body.comment(), authentication.getName());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  record CreateRequest(
      @NotNull Change.Type type,
      @NotNull Change.Risk risk,
      @NotBlank @Size(max = 240) String title,
      Instant plannedStart,
      Instant plannedEnd,
      @NotBlank @Size(max = 20000) String implementationPlan,
      @NotBlank @Size(max = 20000) String rollbackPlan,
      @Size(max = 20000) String testPlan,
      @Size(max = 8000) String businessJustification,
      @Size(max = 8000) String cabNotes,
      Change.Risk cabRiskLevel,
      Change.Impact impact
  ) {}

  record TransitionRequest(
      @NotNull Change.Status target,
      @Size(max = 8000) String cabNotes,
      Change.Risk cabRiskLevel,
      long expectedVersion
  ) {}

  record CabVoteRequest(
      @NotBlank @Size(max = 20) String decision,
      @Size(max = 4000) String comment
  ) {}

  record CabVotesResponse(
      List<CabVoteService.CabVote> votes,
      long approveCount,
      int quorum
  ) {}

  record UpdateRequest(
      long expectedVersion,
      java.time.Instant plannedStart,
      java.time.Instant plannedEnd,
      @Size(max = 20000) String implementationPlan,
      @Size(max = 20000) String rollbackPlan,
      @Size(max = 20000) String testPlan,
      @Size(max = 8000) String businessJustification,
      @Size(max = 8000) String cabNotes,
      Change.Risk cabRiskLevel,
      Change.Impact impact
  ) {}

  record BulkTransitionRequest(@NotNull @Size(min = 1, max = 100) List<@NotNull UUID> ids,
                               @NotNull Change.Status target) {}
  record BulkTransitionResult(UUID id, boolean success, String status, String errorCode) {}
  record BulkTransitionResponse(long succeeded, List<BulkTransitionResult> results) {}

  @PostMapping("/bulk/assign")
  @Operation(summary = "Assign multiple changes to the current user")
  BulkAssignResponse bulkAssign(Authentication authentication,
                                 @Valid @RequestBody BulkAssignRequest body) {
    String actor = authentication.getName();
    String orgId = ru.ultimavox.itsm.platform.authorization.OrganizationContext.current();
    int updated = 0;
    for (UUID id : body.ids()) {
      access.require(actor, "change.write", "change", id.toString());
      int rows = jdbc.update(
          "UPDATE change_request SET owner_subject = ?, version = version + 1, updated_at = now() WHERE id = ? AND org_id = ?",
          actor, id, orgId
      );
      if (rows > 0) updated++;
    }
    return new BulkAssignResponse(updated);
  }

  record BulkAssignRequest(@NotNull @Size(min = 1, max = 100) List<@NotNull UUID> ids) {}
  record BulkAssignResponse(int updated) {}
  record ChangeListResponse(
      List<Change> items,
      int total,
      int page,
      int size
  ) {}
}
