package ru.ultimavox.itsm.assetmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.assetmanagement.application.AssetQuery;
import ru.ultimavox.itsm.assetmanagement.application.LinkAssetToCi;
import ru.ultimavox.itsm.assetmanagement.domain.Asset;
import ru.ultimavox.itsm.platform.authorization.AccessControl;

@RestController
@RequestMapping("/api/v1/assets")
@Tag(name = "Asset Management")
class AssetController {
  private final AssetQuery query;
  private final LinkAssetToCi linkAssetToCi;
  private final AccessControl access;

  AssetController(AssetQuery query, LinkAssetToCi linkAssetToCi, AccessControl access) {
    this.query = query;
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
      return linkAssetToCi.link(id, body.configurationItemId(), authentication.getName());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  record LinkCiRequest(@NotNull UUID configurationItemId) {}
}
