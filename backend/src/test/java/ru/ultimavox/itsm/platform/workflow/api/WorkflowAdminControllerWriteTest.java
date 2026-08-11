package ru.ultimavox.itsm.platform.workflow.api;

import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinitionRepository;

class WorkflowAdminControllerWriteTest {
  @Test void activationRequiresWorkflowWrite() {
    WorkflowDefinitionRepository definitions = Mockito.mock(WorkflowDefinitionRepository.class);
    AccessControl access = Mockito.mock(AccessControl.class);
    Authentication auth = Mockito.mock(Authentication.class);
    Mockito.when(auth.getName()).thenReturn("operator");
    UUID id = UUID.randomUUID();

    try {
      new WorkflowAdminController(definitions, access)
          .setActive(auth, id, new WorkflowAdminController.SetActiveRequest(true));
    } catch (RuntimeException ignored) {
      // Empty repository intentionally returns 404 after authorization.
    }

    verify(access).require("operator", "workflow.write", "workflow_definition", id.toString());
  }
}
