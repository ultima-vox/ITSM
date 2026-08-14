package ru.ultimavox.itsm.platform.automation;

import java.util.Map;
import java.util.UUID;

/** Idempotent action log: unique on (rule_key, event_id, action_type). */
public interface AutomationActionLogRepository {

    /**
     * @return true if the log row was inserted (first execution); false if already present.
     */
    boolean tryLog(String ruleKey, UUID eventId, String actionType, String status, Map<String, Object> details);

    /**
     * Transitions the log row to a terminal status ({@code SUCCEEDED} or {@code FAILED}).
     *
     * @param attempts total action attempts so far (initial execution counts as one)
     */
    void complete(String ruleKey, UUID eventId, String actionType, String status, Map<String, Object> details, int attempts);
}
