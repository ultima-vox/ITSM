package ru.ultimavox.itsm.platform.sla;

import java.time.Instant;
import java.util.UUID;

public record ClockHistoryEntry(
        UUID id,
        UUID clockId,
        Instant occurredAt,
        String action,
        String actorId,
        String details
) {}
