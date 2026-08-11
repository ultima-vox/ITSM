package ru.ultimavox.itsm.platform.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowDefinitionRepository {
    Optional<WorkflowDefinition> findActiveByObjectKey(String objectKey);

    /** All versions for admin list. */
    List<WorkflowDefinitionView> listAll();

    Optional<WorkflowDefinitionView> setActive(UUID id, boolean active);

    record WorkflowDefinitionView(WorkflowDefinition definition, boolean active) {}
}
