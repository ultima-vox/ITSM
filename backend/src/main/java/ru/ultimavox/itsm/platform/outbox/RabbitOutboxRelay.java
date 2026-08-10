package ru.ultimavox.itsm.platform.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** At-least-once relay. Consumers must deduplicate by immutable event ID. */
@Component
class RabbitOutboxRelay {
  private final JdbcTemplate jdbc;
  private final RabbitTemplate rabbit;

  RabbitOutboxRelay(JdbcTemplate jdbc, RabbitTemplate rabbit) {
    this.jdbc = jdbc;
    this.rabbit = rabbit;
  }

  @Scheduled(fixedDelayString = "${itsm.outbox.poll-interval:PT5S}")
  @Transactional
  void relay() {
    List<Pending> pending = jdbc.query(
        """
        SELECT id, event_type, schema_version, correlation_id, causation_id,
               organization_id, actor_id, payload::text, attempts
        FROM outbox_event
        WHERE published_at IS NULL
        ORDER BY occurred_at
        LIMIT 100
        FOR UPDATE SKIP LOCKED
        """,
        (rs, row) -> map(rs));
    pending.forEach(this::publish);
  }

  private Pending map(ResultSet rs) throws SQLException {
    return new Pending(
        rs.getObject("id", UUID.class),
        rs.getString("event_type"),
        rs.getInt("schema_version"),
        rs.getObject("correlation_id", UUID.class),
        rs.getObject("causation_id", UUID.class),
        rs.getString("organization_id"),
        rs.getString("actor_id"),
        rs.getString("payload"),
        rs.getInt("attempts"));
  }

  private void publish(Pending item) {
    try {
      rabbit.convertAndSend(
          RabbitOutboxConfiguration.EXCHANGE,
          item.type(),
          item.payload(),
          message -> {
            var properties = message.getMessageProperties();
            properties.setMessageId(item.id().toString());
            properties.setHeader("event_type", item.type());
            properties.setHeader("schema_version", item.schemaVersion());
            properties.setHeader("correlation_id", item.correlationId().toString());
            if (item.causationId() != null) {
              properties.setHeader("causation_id", item.causationId().toString());
            }
            properties.setHeader("organization_id", item.organizationId());
            properties.setHeader("actor_id", item.actorId());
            return message;
          });
      jdbc.update(
          "UPDATE outbox_event SET published_at=now(), last_error=NULL "
              + "WHERE id=? AND published_at IS NULL",
          item.id());
    } catch (AmqpException exception) {
      jdbc.update(
          "UPDATE outbox_event SET attempts=attempts+1,last_error=? "
              + "WHERE id=? AND published_at IS NULL",
          truncate(exception.getMessage()),
          item.id());
    }
  }

  private String truncate(String message) {
    return message == null ? "Unknown AMQP error" : message.substring(0, Math.min(message.length(), 1000));
  }

  record Pending(
      UUID id,
      String type,
      int schemaVersion,
      UUID correlationId,
      UUID causationId,
      String organizationId,
      String actorId,
      String payload,
      int attempts
  ) {}
}
