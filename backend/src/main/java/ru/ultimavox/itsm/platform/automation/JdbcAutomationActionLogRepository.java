package ru.ultimavox.itsm.platform.automation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
                    INSERT INTO automation_action_log (rule_key, event_id, action_type, status, details)
                    VALUES (?, ?, ?, ?, ?::jsonb)
                    ON CONFLICT (rule_key, event_id, action_type) DO NOTHING
                    """,
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
}
