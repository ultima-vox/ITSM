package ru.ultimavox.itsm.cmdb.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.cmdb.application.CmdbCommands;
import ru.ultimavox.itsm.cmdb.application.CmdbQuery;
import ru.ultimavox.itsm.cmdb.domain.CiRelationship;
import ru.ultimavox.itsm.cmdb.domain.ConfigurationItem;
import ru.ultimavox.itsm.platform.authorization.AccessControl;

@RestController
@RequestMapping("/api/v1/cmdb")
@Tag(name = "CMDB")
class CmdbController {
  private final CmdbQuery query;
  private final CmdbCommands commands;
  private final AccessControl access;

  CmdbController(CmdbQuery query, CmdbCommands commands, AccessControl access) {
    this.query = query;
    this.commands = commands;
    this.access = access;
  }

  @GetMapping("/cis")
  @Operation(summary = "List or search configuration items")
  List<ConfigurationItem> list(
      Authentication authentication,
      @RequestParam(required = false) String classKey,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String q
  ) {
    access.require(authentication.getName(), "cmdb.read", "configuration-item", null);
    return query.search(classKey, status, q);
  }

  @PostMapping("/cis")
  @Operation(summary = "Create a configuration item")
  ResponseEntity<ConfigurationItem> create(
      Authentication authentication,
      @Valid @RequestBody CreateCiRequest body
  ) {
    // Prefer cmdb.write when granted; ADMIN via admin.full short-circuit
    access.require(authentication.getName(), "cmdb.write", "configuration-item", null);
    try {
      ConfigurationItem.Status st = body.status() == null
          ? ConfigurationItem.Status.OPERATIONAL
          : body.status();
      ConfigurationItem created = commands.create(
          new CmdbCommands.CreateCommand(
              body.name(),
              body.classKey() != null ? body.classKey() : body.kindKey(),
              st,
              body.owner(),
              null
          ),
          authentication.getName()
      );
      return ResponseEntity.status(HttpStatus.CREATED).body(created);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  record CreateCiRequest(
      @NotBlank @Size(max = 240) String name,
      @Size(max = 100) String classKey,
      @Size(max = 100) String kindKey,
      ConfigurationItem.Status status,
      @Size(max = 128) String owner
  ) {}

  @GetMapping("/cis/{id}")
  @Operation(summary = "Get configuration item by id")
  ConfigurationItem get(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "cmdb.read", "configuration-item", id.toString());
    return query.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CI not found"));
  }

  @GetMapping("/cis/{id}/relationships")
  @Operation(summary = "List relationships for a configuration item")
  List<CiRelationship> relationships(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "cmdb.read", "configuration-item", id.toString());
    if (query.findById(id).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CI not found");
    }
    return query.relationshipsFor(id);
  }

  @GetMapping("/orphans")
  @Operation(summary = "List configuration items with no relationships (orphan detection)")
  List<ConfigurationItem> orphans(
      Authentication authentication,
      @RequestParam(required = false, defaultValue = "100") int limit
  ) {
    access.require(authentication.getName(), "cmdb.read", "configuration-item", null);
    return query.listOrphans(limit);
  }

  @GetMapping({"/relations", "/relationships"})
  @Operation(summary = "List CI relationships for the dependency graph")
  List<CiRelationship> listRelationships(
      Authentication authentication,
      @RequestParam(required = false, defaultValue = "2000") int limit
  ) {
    access.require(authentication.getName(), "cmdb.read", "configuration-item", null);
    return query.listAllRelationships(limit);
  }

  @PostMapping({"/relations", "/relationships"})
  @Operation(summary = "Create a CI relationship")
  @ResponseStatus(HttpStatus.CREATED)
  CiRelationship createRelationship(
      Authentication authentication,
      @Valid @RequestBody CreateRelationRequest body
  ) {
    access.require(authentication.getName(), "cmdb.write", "configuration-item", null);
    try {
      UUID source = body.sourceCiId() != null ? body.sourceCiId() : body.fromId();
      UUID target = body.targetCiId() != null ? body.targetCiId() : body.toId();
      CiRelationship.Type type = body.type() != null
          ? body.type()
          : mapFrontendRelType(body.relationType());
      return commands.createRelationship(source, target, type, authentication.getName());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @DeleteMapping({"/relations/{id}", "/relationships/{id}"})
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a CI relationship")
  void deleteRelationship(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "cmdb.write", "configuration-item", id.toString());
    try {
      commands.deleteRelationship(id, authentication.getName());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    }
  }

  @PatchMapping({"/relations/{id}", "/relationships/{id}"})
  @Operation(summary = "Update a CI relationship type")
  CiRelationship updateRelationship(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateRelationRequest body
  ) {
    access.require(authentication.getName(), "cmdb.write", "configuration-item", id.toString());
    try {
      return commands.updateRelationship(id, mapFrontendRelType(body.type()), authentication.getName());
    } catch (IllegalArgumentException ex) {
      HttpStatus status = ex.getMessage() != null && ex.getMessage().startsWith("Relationship not found")
          ? HttpStatus.NOT_FOUND
          : HttpStatus.BAD_REQUEST;
      throw new ResponseStatusException(status, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  record UpdateRelationRequest(String type) {}

  record CreateRelationRequest(
      UUID sourceCiId,
      UUID targetCiId,
      UUID fromId,
      UUID toId,
      CiRelationship.Type type,
      String relationType
  ) {}

  private static CiRelationship.Type mapFrontendRelType(String raw) {
    if (raw == null || raw.isBlank()) {
      return CiRelationship.Type.DEPENDS_ON;
    }
    String n = raw.trim().toUpperCase().replace('-', '_');
    return switch (n) {
      case "HOSTED_ON", "HOSTS" -> CiRelationship.Type.HOSTED_ON;
      case "RUNS_ON" -> CiRelationship.Type.RUNS_ON;
      case "USES", "LOCATED_IN" -> CiRelationship.Type.USES;
      case "CONNECTED_TO", "CONNECTS_TO" -> CiRelationship.Type.CONNECTED_TO;
      case "DEPENDS_ON" -> CiRelationship.Type.DEPENDS_ON;
      default -> throw new IllegalArgumentException("Unsupported relationship type: " + raw);
    };
  }

  @GetMapping("/cis/{id}/impact")
  @Operation(summary = "Impact analysis (BFS over CI relationships, up to 8 hops)")
  CmdbQuery.ImpactResult impact(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestParam(required = false, defaultValue = "2") int hops
  ) {
    access.require(authentication.getName(), "cmdb.read", "configuration-item", id.toString());
    try {
      return query.impactAnalysis(id, hops);
    } catch (IllegalArgumentException ex) {
      String msg = ex.getMessage() == null ? "" : ex.getMessage();
      if (msg.contains("maxHops")) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
      }
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
    }
  }
}
