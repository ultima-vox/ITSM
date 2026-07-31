package ru.ultimavox.itsm.platform.workflow;

import java.time.Instant;
import java.util.UUID;

/** Live workflow state for a single domain object. */
public record WorkflowInstance(
        UUID id,
        String objectType,
        String objectId,
        String state,
        int definitionVersion,
        int version,
        Instant updatedAt
) {}
