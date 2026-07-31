package ru.ultimavox.itsm.problemmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
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
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.problemmanagement.application.ProblemCommands;
import ru.ultimavox.itsm.problemmanagement.application.ProblemQuery;
import ru.ultimavox.itsm.problemmanagement.domain.Problem;

@RestController
@RequestMapping("/api/v1/problems")
@Tag(name = "Problem Management")
class ProblemController {
  private final ProblemQuery query;
  private final ProblemCommands commands;
  private final AccessControl access;

  ProblemController(ProblemQuery query, ProblemCommands commands, AccessControl access) {
    this.query = query;
    this.commands = commands;
    this.access = access;
  }

  @GetMapping
  @Operation(summary = "List problems")
  List<ProblemQuery.ProblemSummary> list(
      Authentication authentication,
      @RequestParam(required = false) String status
  ) {
    access.require(authentication.getName(), "problem.read", "problem", null);
    return query.list(status);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get problem by id")
  Problem get(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "problem.read", "problem", id.toString());
    return query.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found"));
  }

  @PostMapping
  @Operation(summary = "Create a problem")
  ResponseEntity<Problem> create(Authentication authentication, @Valid @RequestBody CreateRequest body) {
    access.require(authentication.getName(), "problem.write", "problem", null);
    Problem created = commands.create(
        new ProblemCommands.CreateCommand(body.title(), body.rootCause(), body.workaround()),
        authentication.getName()
    );
    return ResponseEntity.created(URI.create("/api/v1/problems/" + created.id())).body(created);
  }

  @PostMapping("/{id}/transitions")
  @Operation(summary = "Transition problem lifecycle status")
  Problem transition(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody TransitionRequest body
  ) {
    access.require(authentication.getName(), "problem.write", "problem", id.toString());
    try {
      return commands.transition(
          id,
          body.target(),
          body.rootCause(),
          body.workaround(),
          body.resolution(),
          authentication.getName()
      );
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @PostMapping("/{id}/work-items")
  @Operation(summary = "Link a work item to a problem")
  Problem linkWorkItem(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody LinkWorkItemRequest body
  ) {
    access.require(authentication.getName(), "problem.write", "problem", id.toString());
    try {
      return commands.linkWorkItem(id, body.workItemId(), authentication.getName());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    }
  }

  record CreateRequest(
      @NotBlank @Size(max = 240) String title,
      @Size(max = 12000) String rootCause,
      @Size(max = 12000) String workaround
  ) {}

  record TransitionRequest(
      @NotNull Problem.Status target,
      @Size(max = 12000) String rootCause,
      @Size(max = 12000) String workaround,
      @Size(max = 12000) String resolution
  ) {}

  record LinkWorkItemRequest(@NotNull UUID workItemId) {}
}
