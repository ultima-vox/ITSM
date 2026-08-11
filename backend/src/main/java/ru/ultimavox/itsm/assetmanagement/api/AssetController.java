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

  AssetController(
      AssetQuery query,
      AssetCommands commands,
      CreateAsset createAsset,
      LinkAssetToCi linkAssetToCi,
      AccessControl access
  ) {
    this.query = query;
    this.commands = commands;
    this.createAsset = createAsset;
    this.linkAssetToCi = linkAssetToCi;
    this.access = access;
  }

  @GetMapping
  @Operation(summary = "List assets")
  List<Asset> list(
      Authentication authentication,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) String owner
  ) {
    access.require(authentication.getName(), "asset.read", "asset", null);
    return query.list(status, kind, owner);
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
              body.kind(),
              body.status(),
              body.ownerSubject() != null ? body.ownerSubject() : body.assignedTo(),
              body.configurationItemId(),
              body.acquiredOn(),
              body.warrantyUntil()
          ),
          authentication.getName()
      );
      return ResponseEntity.status(HttpStatus.CREATED).body(created);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  record CreateAssetRequest(
      @Size(max = 80) String assetTag,
      @Size(max = 80) String tag,
      Asset.Kind kind,
      Asset.Status status,
      @Size(max = 128) String ownerSubject,
      @Size(max = 128) String assignedTo,
      UUID configurationItemId,
      LocalDate acquiredOn,
      LocalDate warrantyUntil
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
