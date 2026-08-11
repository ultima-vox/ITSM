package ru.ultimavox.itsm.platform.authorization.api;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import java.time.Instant;
import java.util.UUID;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.authorization.RbacRepository;
import ru.ultimavox.itsm.platform.authorization.RoleDelegationService;

class RbacAdminControllerWriteTest {
  @Test void replacementRequiresRbacWrite() {
    RbacRepository rbac = Mockito.mock(RbacRepository.class);
    AccessControl access = Mockito.mock(AccessControl.class);
    Authentication auth = Mockito.mock(Authentication.class);
    Mockito.when(auth.getName()).thenReturn("operator");
    Mockito.when(rbac.replacePrincipalRole("subject-1", "REQUESTER"))
        .thenReturn(new RbacRepository.PrincipalAssignment("subject-1", java.util.List.of("REQUESTER")));

    new RbacAdminController(rbac, access, org.mockito.Mockito.mock(RoleDelegationService.class)).replaceRole(
        auth, "subject-1", new RbacAdminController.ReplaceRoleRequest("REQUESTER"));

    verify(access).require("operator", "rbac.write", "principal_role", "subject-1");
    verify(rbac).replacePrincipalRole("subject-1", "REQUESTER");
  }

  @Test void delegationRequiresDedicatedPermission() {
    RbacRepository rbac = Mockito.mock(RbacRepository.class);
    AccessControl access = Mockito.mock(AccessControl.class);
    RoleDelegationService delegations = Mockito.mock(RoleDelegationService.class);
    Authentication auth = Mockito.mock(Authentication.class);
    Mockito.when(auth.getName()).thenReturn("operator");
    Instant expiry = Instant.now().plusSeconds(3600);
    var request = new RbacAdminController.DelegationRequest(
        "manager", "stand-in", "SERVICE_DESK_MANAGER", null, expiry, "coverage");
    Mockito.when(delegations.create(Mockito.any(), Mockito.eq("operator")))
        .thenReturn(new RoleDelegationService.Delegation(UUID.randomUUID(), "manager", "stand-in",
            "SERVICE_DESK_MANAGER", Instant.now(), expiry, "coverage", "operator",
            Instant.now(), null, null));

    new RbacAdminController(rbac, access, delegations).createDelegation(auth, request);

    verify(access).require("operator", "rbac.delegate", "role-delegation", null);
    verify(delegations).create(Mockito.any(), Mockito.eq("operator"));
  }
}
