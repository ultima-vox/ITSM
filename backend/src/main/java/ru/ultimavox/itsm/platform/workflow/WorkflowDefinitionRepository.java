package ru.ultimavox.itsm.platform.workflow;

import java.util.Optional;

public interface WorkflowDefinitionRepository {
    Optional<WorkflowDefinition> findActiveByObjectKey(String objectKey);
}
