package ru.ultimavox.itsm.platform.authorization.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.authorization.RbacRepository;
import ru.ultimavox.itsm.platform.authorization.RoleDelegationService;

class RbacEffectiveAccessControllerTest {
  @Test
  void returnsAuthenticatedPrincipalsEffectiveAccess() {
    RbacRepository rbac = mock(RbacRepository.class);
    Authentication authentication = mock(Authentication.class);
    when(authentication.getName()).thenReturn("alice");
    when(rbac.rolesForSubject("alice")).thenReturn(Set.of("SERVICE_DESK_AGENT"));
    when(rbac.permissionsForSubject("alice")).thenReturn(Set.of("work-item.read", "work-item.update"));
    var controller = new RbacAdminController(
        rbac, mock(AccessControl.class), mock(RoleDelegationService.class));

    var response = controller.effectiveAccess(authentication);

    assertThat(response.subjectId()).isEqualTo("alice");
    assertThat(response.roles()).containsExactly("SERVICE_DESK_AGENT");
    assertThat(response.permissions()).containsExactly("work-item.read", "work-item.update");
  }
}
