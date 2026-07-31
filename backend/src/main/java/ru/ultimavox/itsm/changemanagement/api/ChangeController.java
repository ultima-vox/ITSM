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
  private final AccessControl access;

  ChangeController(ChangeQuery query, ChangeCommands commands, AccessControl access) {
    this.query = query;
    this.commands = commands;
    this.access = access;
  }

  @GetMapping
  @Operation(summary = "List changes")
  List<Change> list(Authentication authentication, @RequestParam(required = false) String status) {
    access.require(authentication.getName(), "change.read", "change", null);
    return query.list(status);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get change by id")
  Change get(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "change.read", "change", id.toString());
    return query.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change not found"));
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
            body.businessJustification(),
            body.cabNotes(),
            body.cabRiskLevel()
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
          id, body.target(), body.cabNotes(), body.cabRiskLevel(), authentication.getName()
      );
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
      @Size(max = 8000) String businessJustification,
      @Size(max = 8000) String cabNotes,
      Change.Risk cabRiskLevel
  ) {}

  record TransitionRequest(
      @NotNull Change.Status target,
      @Size(max = 8000) String cabNotes,
      Change.Risk cabRiskLevel
  ) {}
}
