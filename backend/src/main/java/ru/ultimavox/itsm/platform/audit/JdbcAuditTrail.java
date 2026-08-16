package ru.ultimavox.itsm.platform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcAuditTrail implements AuditTrail {

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  JdbcAuditTrail(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public void append(Entry e) {
    jdbc.update(
        """
            INSERT INTO audit_event (
              actor_id, action, object_type, object_id,
              before_state, after_state, correlation_id, occurred_at
            ) VALUES (?,?,?,?,?::jsonb,?::jsonb,?,?)
            """,
        e.actorId(),
        e.action(),
        e.objectType(),
        e.objectId(),
        write(e.before()),
        write(e.after()),
        e.correlationId(),
        Timestamp.from(e.occurredAt())
    );
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize audit state", ex);
    }
  }
}
