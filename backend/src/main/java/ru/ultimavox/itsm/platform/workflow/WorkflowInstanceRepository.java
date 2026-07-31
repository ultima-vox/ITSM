package ru.ultimavox.itsm.platform.workflow;

import java.util.Optional;

public interface WorkflowInstanceRepository {

    Optional<WorkflowInstance> findByObject(String objectType, String objectId);

    WorkflowInstance insert(WorkflowInstance instance);

    WorkflowInstance updateState(WorkflowInstance instance, String newState, int expectedVersion);
}
