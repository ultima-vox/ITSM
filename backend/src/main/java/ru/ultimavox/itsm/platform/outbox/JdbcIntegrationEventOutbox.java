package ru.ultimavox.itsm.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.event.AutomationDepthContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.event.DomainEventEnvelope;

@Component
class JdbcIntegrationEventOutbox implements IntegrationEventOutbox {

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final ApplicationEventPublisher events;

  JdbcIntegrationEventOutbox(JdbcTemplate jdbc, ObjectMapper json, ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.json = json;
    this.events = events;
  }

  @Override
  public void record(DomainEvent e) {
    try {
      jdbc.update(
          """
              INSERT INTO outbox_event (
                id, occurred_at, event_type, schema_version, correlation_id, causation_id,
                organization_id, actor_id, aggregate_type, aggregate_id, payload
              ) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb)
              """,
          e.id(),
          Timestamp.from(e.occurredAt()),
          e.type(),
          e.schemaVersion(),
          e.correlationId(),
          e.causationId(),
          e.organizationId(),
          e.actorId(),
          e.aggregateType(),
          e.aggregateId(),
          json.writeValueAsString(e)
      );
      events.publishEvent(new DomainEventEnvelope(e, AutomationDepthContext.current()));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize domain event", ex);
    }
  }
}
