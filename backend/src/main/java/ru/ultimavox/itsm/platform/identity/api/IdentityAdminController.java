package ru.ultimavox.itsm.platform.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.identity.IdentityQueryService;
import ru.ultimavox.itsm.platform.identity.IdentityQueryService.GroupRoleMappingRecord;
import ru.ultimavox.itsm.platform.identity.IdentityQueryService.IdentityAccountRecord;

@RestController
@RequestMapping("/api/v1/identity")
@Tag(name = "Platform — Identity")
class IdentityAdminController {
  private final IdentityQueryService query;
  private final AccessControl access;

  IdentityAdminController(IdentityQueryService query, AccessControl access) {
    this.query = query;
    this.access = access;
  }

  @GetMapping("/accounts")
  @Operation(summary = "List identity accounts and assigned role keys")
  List<IdentityAccountResponse> listAccounts(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "200") int size
  ) {
    requireRead(authentication, "identity_account");
    return query.listAccounts(page, size).stream().map(IdentityAccountResponse::from).toList();
  }

  @GetMapping("/group-mappings")
  @Operation(summary = "List IdP group to ITSM role mappings")
  List<GroupRoleMappingResponse> listGroupMappings(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "200") int size
  ) {
    requireRead(authentication, "group_role_mapping");
    return query.listGroupMappings(page, size).stream().map(GroupRoleMappingResponse::from).toList();
  }

  private void requireRead(Authentication authentication, String objectType) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "rbac.read", objectType, null);
  }

  record IdentityAccountResponse(
      UUID id,
      String idp,
      String externalId,
      String subjectId,
      boolean enabled,
      Instant lastSync,
      List<String> roleKeys
  ) {
    static IdentityAccountResponse from(IdentityAccountRecord row) {
      return new IdentityAccountResponse(
          row.id(),
          row.idp(),
          row.externalId(),
          row.subjectId(),
          row.enabled(),
          row.lastSync(),
          row.roleKeys()
      );
    }
  }

  record GroupRoleMappingResponse(String idpGroup, String roleName) {
    static GroupRoleMappingResponse from(GroupRoleMappingRecord row) {
      return new GroupRoleMappingResponse(row.idpGroup(), row.roleName());
    }
  }
}
