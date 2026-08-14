package ru.ultimavox.itsm.platform.automation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Read model for a persisted automation_action_log row. */
public record AutomationActionLogEntry(
    UUID id,
    String ruleKey,
    UUID eventId,
    String actionType,
    String status,
    Map<String, Object> details,
    int attempts,
    Instant createdAt
) {}
