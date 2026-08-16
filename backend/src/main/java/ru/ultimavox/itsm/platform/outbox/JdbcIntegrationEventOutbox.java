package ru.ultimavox.itsm.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.event.DomainEvent;

@Component
class JdbcIntegrationEventOutbox implements IntegrationEventOutbox {

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  JdbcIntegrationEventOutbox(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public void record(DomainEvent e) {
    try {
      jdbc.update(
          """
              INSERT INTO outbox_event (
                id, occurred_at, event_type, aggregate_type, aggregate_id, payload
              ) VALUES (?,?,?,?,?,?::jsonb)
              """,
          e.id(),
          Timestamp.from(e.occurredAt()),
          e.type(),
          e.aggregateType(),
          e.aggregateId(),
          json.writeValueAsString(e)
      );
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize domain event", ex);
    }
  }
}
