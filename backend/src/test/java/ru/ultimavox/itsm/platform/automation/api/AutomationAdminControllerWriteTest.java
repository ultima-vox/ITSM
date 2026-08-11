package ru.ultimavox.itsm.platform.automation.api;

import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.automation.AutomationRuleRepository;

class AutomationAdminControllerWriteTest {
  @Test void toggleRequiresAutomationWrite() {
    AutomationRuleRepository rules = Mockito.mock(AutomationRuleRepository.class);
    AccessControl access = Mockito.mock(AccessControl.class);
    Authentication auth = Mockito.mock(Authentication.class);
    Mockito.when(auth.getName()).thenReturn("operator");
    UUID id = UUID.randomUUID();

    try {
      new AutomationAdminController(rules, access)
          .setEnabled(auth, id, new AutomationAdminController.SetEnabledRequest(true));
    } catch (RuntimeException ignored) {
      // Empty repository intentionally returns 404 after authorization.
    }

    verify(access).require("operator", "automation.write", "automation_rule", id.toString());
  }
}
