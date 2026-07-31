package ru.ultimavox.itsm.platform.sla;

import java.time.Instant;
import java.util.UUID;

/** Persisted operational SLA state. Each pause/resume/recalculation creates an immutable history record. */
public record SlaClock(
        UUID id,
        String policyKey,
        UUID aggregateId,
        String metric,
        Instant startedAt,
        Instant dueAt,
        Instant warningAt,
        State state
) {
    public enum State {
        RUNNING, PAUSED, ACHIEVED, BREACHED, CANCELLED
    }
}
