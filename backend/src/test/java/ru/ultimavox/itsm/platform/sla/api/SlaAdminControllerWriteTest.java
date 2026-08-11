package ru.ultimavox.itsm.platform.sla.api;

import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.sla.SlaPolicyRepository;
import ru.ultimavox.itsm.platform.sla.SlaPolicyAdminService;

class SlaAdminControllerWriteTest {
  @Test void updateRequiresSlaWrite() {
    SlaPolicyRepository policies = Mockito.mock(SlaPolicyRepository.class);
    AccessControl access = Mockito.mock(AccessControl.class);
    Authentication auth = Mockito.mock(Authentication.class);
    Mockito.when(auth.getName()).thenReturn("operator");
    UUID id = UUID.randomUUID();

    try {
      new SlaAdminController(policies, Mockito.mock(SlaPolicyAdminService.class), access,
          Mockito.mock(ru.ultimavox.itsm.platform.sla.WorkingCalendarAdminService.class))
          .updatePolicy(auth, id, new SlaAdminController.UpdatePolicyRequest(1, false, null));
    } catch (RuntimeException ignored) {
      // Empty repository intentionally returns 404 after authorization.
    }

    verify(access).require("operator", "sla.write", "sla_policy", id.toString());
  }
}
