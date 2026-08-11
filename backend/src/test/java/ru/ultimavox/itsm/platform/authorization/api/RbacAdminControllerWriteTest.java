package ru.ultimavox.itsm.platform.authorization.api;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.authorization.RbacRepository;

class RbacAdminControllerWriteTest {
  @Test void replacementRequiresRbacWrite() {
    RbacRepository rbac = Mockito.mock(RbacRepository.class);
    AccessControl access = Mockito.mock(AccessControl.class);
    Authentication auth = Mockito.mock(Authentication.class);
    Mockito.when(auth.getName()).thenReturn("operator");
    Mockito.when(rbac.replacePrincipalRole("subject-1", "REQUESTER"))
        .thenReturn(new RbacRepository.PrincipalAssignment("subject-1", java.util.List.of("REQUESTER")));

    new RbacAdminController(rbac, access).replaceRole(
        auth, "subject-1", new RbacAdminController.ReplaceRoleRequest("REQUESTER"));

    verify(access).require("operator", "rbac.write", "principal_role", "subject-1");
    verify(rbac).replacePrincipalRole("subject-1", "REQUESTER");
  }
}
