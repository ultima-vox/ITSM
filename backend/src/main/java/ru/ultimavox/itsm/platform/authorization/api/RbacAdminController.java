package ru.ultimavox.itsm.platform.authorization.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.authorization.RbacRepository;
import ru.ultimavox.itsm.platform.authorization.RoleDelegationService;
import ru.ultimavox.itsm.platform.authorization.RbacRepository.PermissionCatalogEntry;
import ru.ultimavox.itsm.platform.authorization.RbacRepository.PrincipalAssignment;
import ru.ultimavox.itsm.platform.authorization.RbacRepository.RoleCatalogEntry;

@RestController
@RequestMapping("/api/v1/rbac")
@Tag(name = "Platform — RBAC")
class RbacAdminController {

  private final RbacRepository rbac;
  private final AccessControl access;
  private final RoleDelegationService delegations;

  RbacAdminController(RbacRepository rbac, AccessControl access, RoleDelegationService delegations) {
    this.rbac = rbac;
    this.access = access;
    this.delegations = delegations;
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

  @GetMapping("/me")
  @Operation(summary = "Get effective roles and permissions for authenticated principal")
  EffectiveAccessResponse effectiveAccess(Authentication authentication) {
    if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated principal is required");
    }
    String subject = authentication.getName();
    return new EffectiveAccessResponse(
        subject,
        rbac.rolesForSubject(subject).stream().sorted().toList(),
        rbac.permissionsForSubject(subject).stream().sorted().toList()
    );
  }

  record EffectiveAccessResponse(String subjectId, List<String> roles, List<String> permissions) {}

  @GetMapping("/principals")
  @Operation(summary = "List principal role assignments")
  List<PrincipalResponse> listPrincipals(Authentication authentication) {
    requireRead(authentication);
    return rbac.listPrincipalAssignments().stream()
        .map(p -> new PrincipalResponse(p.subjectId(), p.roleKeys()))
        .toList();
  }

  @PutMapping("/principals/{subjectId}/role")
  @Operation(summary = "Replace principal role assignment in current organization")
  PrincipalResponse replaceRole(
      Authentication authentication,
      @PathVariable String subjectId,
      @RequestBody ReplaceRoleRequest body
  ) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "rbac.write", "principal_role", subjectId);
    if (subjectId == null || subjectId.isBlank() || body.roleKey() == null || body.roleKey().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subjectId and roleKey are required");
    }
    try {
      return PrincipalResponse.from(rbac.replacePrincipalRole(subjectId, body.roleKey()));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  record ReplaceRoleRequest(String roleKey) {}

  @GetMapping("/delegations")
  @Operation(summary = "List temporary role delegations in current organization")
  List<RoleDelegationService.Delegation> listDelegations(Authentication authentication) {
    access.require(authentication.getName(), "rbac.read", "role-delegation", null);
    return delegations.list();
  }

  @PostMapping("/delegations")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a bounded temporary role delegation")
  RoleDelegationService.Delegation createDelegation(
      Authentication authentication, @RequestBody DelegationRequest body) {
    String actor = authentication.getName();
    access.require(actor, "rbac.delegate", "role-delegation", null);
    try {
      return delegations.create(new RoleDelegationService.Command(
          body.delegatorId(), body.delegateeId(), body.roleKey(), body.startsAt(),
          body.expiresAt(), body.reason()), actor);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @DeleteMapping("/delegations/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Revoke an active temporary role delegation")
  void revokeDelegation(Authentication authentication, @PathVariable UUID id) {
    String actor = authentication.getName();
    access.require(actor, "rbac.delegate", "role-delegation", id.toString());
    try {
      delegations.revoke(id, actor);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    }
  }

  record DelegationRequest(String delegatorId, String delegateeId, String roleKey,
                           Instant startsAt, Instant expiresAt, String reason) {}

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

  record PrincipalResponse(String subjectId, List<String> roleKeys) {
    static PrincipalResponse from(PrincipalAssignment assignment) {
      return new PrincipalResponse(assignment.subjectId(), assignment.roleKeys());
    }
  }
}
