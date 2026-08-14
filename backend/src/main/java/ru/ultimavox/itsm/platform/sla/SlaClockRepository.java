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

    /** RUNNING clocks that reached warning_at but are not yet due and have not been warned. */
    List<SlaClock> findDueForWarning(int limit);

    /** Idempotent marker so a warning is emitted once per clock. */
    void markWarned(UUID clockId);

    /** All organizations holding RUNNING clocks that are due or warning-due. */
    List<String> distinctOrgIdsWithDueOrWarnClocks();

    /**
     * Marks every active clock of the given aggregate as ACHIEVED and records history.
     *
     * @return number of clocks stopped
     */
    int achieveFor(UUID aggregateId, String actorId);
}
