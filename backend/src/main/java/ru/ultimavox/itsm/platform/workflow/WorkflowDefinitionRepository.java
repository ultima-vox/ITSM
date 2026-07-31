package ru.ultimavox.itsm.platform.workflow;

import java.util.List;
import java.util.Optional;

public interface WorkflowDefinitionRepository {
    Optional<WorkflowDefinition> findActiveByObjectKey(String objectKey);

    /** All versions for admin list. */
    List<WorkflowDefinitionView> listAll();

    record WorkflowDefinitionView(WorkflowDefinition definition, boolean active) {}
}
