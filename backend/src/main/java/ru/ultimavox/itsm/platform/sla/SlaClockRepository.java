package ru.ultimavox.itsm.platform.sla;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlaClockRepository {

    SlaClock insert(SlaClock clock);

    Optional<SlaClock> findById(UUID id);

    List<SlaClock> findActiveByAggregate(UUID aggregateId);

    Optional<SlaClock> findActive(UUID aggregateId, String policyKey, String metric);

    SlaClock update(SlaClock clock);

    void appendHistory(UUID clockId, String action, String actorId, String detailsJson);

    List<SlaClock> findDueRunning(int limit);
}
