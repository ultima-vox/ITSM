package ru.ultimavox.itsm.platform.authorization.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.authorization.RbacRepository;
import ru.ultimavox.itsm.platform.authorization.RbacRepository.PermissionCatalogEntry;
import ru.ultimavox.itsm.platform.authorization.RbacRepository.PrincipalAssignment;
import ru.ultimavox.itsm.platform.authorization.RbacRepository.RoleCatalogEntry;

@RestController
@RequestMapping("/api/v1/rbac")
@Tag(name = "Platform — RBAC")
class RbacAdminController {

  private final RbacRepository rbac;
  private final AccessControl access;

  RbacAdminController(RbacRepository rbac, AccessControl access) {
    this.rbac = rbac;
    this.access = access;
  }

  @GetMapping("/roles")
  @Operation(summary = "List RBAC roles with permission keys")
  List<RoleResponse> listRoles(Authentication authentication) {
    requireRead(authentication);
    return rbac.listRoles().stream().map(RoleResponse::from).toList();
  }

  @GetMapping("/permissions")
  @Operation(summary = "List permission catalog")
  List<PermissionResponse> listPermissions(Authentication authentication) {
    requireRead(authentication);
    return rbac.listPermissions().stream()
        .map(p -> new PermissionResponse(p.key(), p.description()))
        .toList();
  }

  @GetMapping("/principals")
  @Operation(summary = "List principal role assignments")
  List<PrincipalResponse> listPrincipals(Authentication authentication) {
    requireRead(authentication);
    return rbac.listPrincipalAssignments().stream()
        .map(p -> new PrincipalResponse(p.subjectId(), p.roleKeys()))
        .toList();
  }

  private void requireRead(Authentication authentication) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "rbac.read", "role", null);
  }

  record RoleResponse(
      UUID id,
      String roleKey,
      Map<String, String> labels,
      String description,
      List<String> permissions
  ) {
    static RoleResponse from(RoleCatalogEntry e) {
      return new RoleResponse(
          e.id(),
          e.roleKey(),
          e.labels(),
          e.description(),
          e.permissions()
      );
    }
  }

  record PermissionResponse(String key, String description) {}

  record PrincipalResponse(String subjectId, List<String> roleKeys) {}
}
