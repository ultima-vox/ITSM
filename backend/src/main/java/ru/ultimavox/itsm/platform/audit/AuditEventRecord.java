package ru.ultimavox.itsm.platform.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Read model for a persisted audit_event row. */
public record AuditEventRecord(
    UUID id,
    Instant occurredAt,
    String actorId,
    String action,
    String objectType,
    String objectId,
    Map<String, Object> beforeState,
    Map<String, Object> afterState,
    UUID correlationId,
    Map<String, Object> metadata
) {}
