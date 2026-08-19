package ru.ultimavox.itsm.assetmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.assetmanagement.application.AssetCommands;
import ru.ultimavox.itsm.assetmanagement.application.AssetQuery;
import ru.ultimavox.itsm.assetmanagement.application.CreateAsset;
import ru.ultimavox.itsm.assetmanagement.application.LinkAssetToCi;
import ru.ultimavox.itsm.assetmanagement.domain.Asset;
import ru.ultimavox.itsm.platform.authorization.AccessControl;

@RestController
@RequestMapping("/api/v1/assets")
@Tag(name = "Asset Management")
class AssetController {
  private final AssetQuery query;
  private final AssetCommands commands;
  private final CreateAsset createAsset;
  private final LinkAssetToCi linkAssetToCi;
  private final AccessControl access;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  AssetController(
      AssetQuery query,
      AssetCommands commands,
      CreateAsset createAsset,
      LinkAssetToCi linkAssetToCi,
      AccessControl access,
      org.springframework.jdbc.core.JdbcTemplate jdbc
  ) {
    this.query = query;
    this.commands = commands;
    this.createAsset = createAsset;
    this.linkAssetToCi = linkAssetToCi;
    this.access = access;
    this.jdbc = jdbc;
  }

  @GetMapping
  @Operation(summary = "List assets")
  List<Asset> list(
      Authentication authentication,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) String owner,
      @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 2000) String q
  ) {
    access.require(authentication.getName(), "asset.read", "asset", null);
    return query.list(status, kind, owner, q);
  }

  @PostMapping
  @Operation(summary = "Create an asset")
  ResponseEntity<Asset> create(
      Authentication authentication,
      @Valid @RequestBody CreateAssetRequest body
  ) {
    access.require(authentication.getName(), "asset.write", "asset", null);
    try {
      String tag = body.assetTag() != null ? body.assetTag() : body.tag();
      Asset created = createAsset.create(
          new CreateAsset.Command(
              tag,
              body.name(),
              body.kind(),
              body.status(),
              body.ownerSubject() != null ? body.ownerSubject() : body.assignedTo(),
              body.configurationItemId(),
              body.acquiredOn(),
              body.warrantyUntil(),
              body.location()
          ),
          authentication.getName()
      );
      return ResponseEntity.status(HttpStatus.CREATED).body(created);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update asset fields (name, location)")
  Asset patch(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody PatchAssetRequest body
  ) {
    access.require(authentication.getName(), "asset.write", "asset", id.toString());
    try {
      Asset current = query.findById(id)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
      if (body.expectedVersion() >= 0 && current.version() != body.expectedVersion()) {
        throw new org.springframework.dao.OptimisticLockingFailureException("Asset changed since version " + body.expectedVersion());
      }
      Asset updated = current.updateFields(body.name(), body.location());
      // Persist directly since AssetCommands.persist expects a before snapshot
      java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
      jdbc.update("""
          UPDATE asset SET name = ?, location = ?, version = version + 1, updated_at = ?
          WHERE id = ? AND org_id = ? AND version = ?
          """,
          updated.name(), updated.location(), now, id,
          ru.ultimavox.itsm.platform.authorization.OrganizationContext.current(), current.version());
      return query.findById(id).orElseThrow();
    } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  record CreateAssetRequest(
      @Size(max = 80) String assetTag,
      @Size(max = 80) String tag,
      @Size(max = 240) String name,
      Asset.Kind kind,
      Asset.Status status,
      @Size(max = 128) String ownerSubject,
      @Size(max = 128) String assignedTo,
      UUID configurationItemId,
      LocalDate acquiredOn,
      LocalDate warrantyUntil,
      @Size(max = 240) String location
  ) {}

  record PatchAssetRequest(
      @Size(max = 240) String name,
      @Size(max = 240) String location,
      long expectedVersion
  ) {}

  @GetMapping("/{id}")
  @Operation(summary = "Get asset by id")
  Asset get(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "asset.read", "asset", id.toString());
    return query.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
  }

  @PostMapping("/{id}/link-ci")
  @Operation(summary = "Link asset to a configuration item")
  Asset linkCi(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody LinkCiRequest body
  ) {
    access.require(authentication.getName(), "asset.write", "asset", id.toString());
    try {
      return linkAssetToCi.link(id, body.configurationItemId(), body.expectedVersion(), authentication.getName());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @PostMapping("/{id}/assign")
  @Operation(summary = "Assign asset to an owner subject")
  Asset assign(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody AssignRequest body
  ) {
    access.require(authentication.getName(), "asset.write", "asset", id.toString());
    try {
      return commands.assign(id, body.ownerSubject(), body.expectedVersion(), authentication.getName());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @GetMapping("/transitions")
  @Operation(summary = "List available target statuses from a given asset status")
  List<String> listTransitions(
      Authentication authentication,
      @RequestParam Asset.Status status
  ) {
    access.require(authentication.getName(), "asset.read", "asset", null);
    return switch (status) {
      case ORDERED -> List.of("IN_STOCK", "RETIRED");
      case IN_STOCK -> List.of("IN_USE", "REPAIRED", "RETIRED");
      case IN_USE -> List.of("IN_STOCK", "REPAIRED", "LOST", "RETIRED");
      case REPAIRED -> List.of("IN_STOCK", "IN_USE", "RETIRED");
      case LOST, RETIRED -> List.of();
    };
  }

  @PostMapping({"/{id}/transition", "/{id}/transitions"})
  @Operation(summary = "Transition asset lifecycle status")
  Asset transition(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody TransitionRequest body
  ) {
    access.require(authentication.getName(), "asset.write", "asset", id.toString());
    try {
      return commands.transition(id, body.status(), body.expectedVersion(), authentication.getName());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  record LinkCiRequest(@NotNull UUID configurationItemId, long expectedVersion) {}

  record AssignRequest(@NotBlank String ownerSubject, long expectedVersion) {}

  record TransitionRequest(@NotNull Asset.Status status, long expectedVersion) {}
}
