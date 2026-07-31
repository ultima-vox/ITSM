package ru.ultimavox.itsm.servicedesk.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads the platform audit trail for a work item (operator activity timeline). */
@Service
public class WorkItemActivityQuery {

  private final WorkItemStore store;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  WorkItemActivityQuery(WorkItemStore store, JdbcTemplate jdbc, ObjectMapper json) {
    this.store = store;
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<ActivityEntry> list(UUID workItemId) {
    store.requireById(workItemId);
    return jdbc.query(
        """
        SELECT id, occurred_at, actor_id, action, before_state, after_state, correlation_id
        FROM audit_event
        WHERE object_type = 'work-item' AND object_id = ?
        ORDER BY occurred_at DESC
        """,
        (rs, rowNum) -> new ActivityEntry(
            (UUID) rs.getObject("id"),
            rs.getTimestamp("occurred_at").toInstant(),
            rs.getString("actor_id"),
            rs.getString("action"),
            readMap(rs.getString("before_state")),
            readMap(rs.getString("after_state")),
            (UUID) rs.getObject("correlation_id")
        ),
        workItemId.toString()
    );
  }

  private Map<String, Object> readMap(String raw) {
    if (raw == null || raw.isBlank()) {
      return Map.of();
    }
    try {
      return json.readValue(raw, new TypeReference<>() {});
    } catch (Exception ex) {
      return Map.of("raw", raw);
    }
  }

  public record ActivityEntry(
      UUID id,
      Instant occurredAt,
      String actorId,
      String action,
      Map<String, Object> before,
      Map<String, Object> after,
      UUID correlationId
  ) {}
}
