package ru.ultimavox.itsm.platform.automation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

import java.util.Map;
import java.util.UUID;

@Repository
class JdbcAutomationActionLogRepository implements AutomationActionLogRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcAutomationActionLogRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean tryLog(String ruleKey, UUID eventId, String actionType, String status, Map<String, Object> details) {
        try {
            int inserted = jdbc.update(
                    """
                    INSERT INTO automation_action_log (org_id, rule_key, event_id, action_type, status, details)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb)
                    ON CONFLICT (org_id, rule_key, event_id, action_type) DO NOTHING
                    """,
                    OrganizationContext.current(),
                    ruleKey,
                    eventId,
                    actionType,
                    status,
                    json.writeValueAsString(details == null ? Map.of() : details)
            );
            return inserted > 0;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize automation action log details", ex);
        }
    }

    @Override
    public void complete(String ruleKey, UUID eventId, String actionType, String status, Map<String, Object> details, int attempts) {
        if (!"SUCCEEDED".equals(status) && !"FAILED".equals(status)) {
            throw new IllegalArgumentException("Automation terminal status must be SUCCEEDED or FAILED");
        }
        try {
            int changed = jdbc.update("""
                    UPDATE automation_action_log
                    SET status = ?, details = ?::jsonb, attempts = ?
                    WHERE org_id = ? AND rule_key = ? AND event_id = ? AND action_type = ?
                    """, status, json.writeValueAsString(details == null ? Map.of() : details), attempts,
                    OrganizationContext.current(), ruleKey, eventId, actionType);
            if (changed != 1) throw new IllegalStateException("Automation action log row is missing");
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize automation action completion", ex);
        }
    }
}
