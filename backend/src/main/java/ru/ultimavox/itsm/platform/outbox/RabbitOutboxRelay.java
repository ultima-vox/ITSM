package ru.ultimavox.itsm.platform.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * At-least-once relay. Consumers must deduplicate by immutable event ID.
 *
 * <p>Publish failures are retried with exponential backoff ({@code next_attempt_at}) and a
 * bounded number of attempts; events that exhaust the budget are quarantined
 * ({@code quarantined_at}) and stop being polled so a poison event can never stall the queue
 * or retry forever. Quarantined rows are retained for operator review and remain un-published.
 */
@Component
class RabbitOutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(RabbitOutboxRelay.class);

  private final JdbcTemplate jdbc;
  private final RabbitTemplate rabbit;
  private final int maxAttempts;
  private final Duration backoffBase;
  private final Duration backoffMax;

  RabbitOutboxRelay(
      JdbcTemplate jdbc,
      RabbitTemplate rabbit,
      @Value("${itsm.outbox.max-attempts:10}") int maxAttempts,
      @Value("${itsm.outbox.backoff-base:PT5S}") Duration backoffBase,
      @Value("${itsm.outbox.backoff-max:PT5M}") Duration backoffMax) {
    this.jdbc = jdbc;
    this.rabbit = rabbit;
    this.maxAttempts = maxAttempts;
    this.backoffBase = backoffBase;
    this.backoffMax = backoffMax;
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
          AND quarantined_at IS NULL
          AND (next_attempt_at IS NULL OR next_attempt_at <= now())
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

  void publish(Pending item) {
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
          "UPDATE outbox_event SET published_at=now(), last_error=NULL, next_attempt_at=NULL "
              + "WHERE id=? AND published_at IS NULL",
          item.id());
    } catch (AmqpException exception) {
      handlePublishFailure(item, exception);
    }
  }

  private void handlePublishFailure(Pending item, AmqpException exception) {
    String error = truncate(exception.getMessage());
    int attempts = item.attempts() + 1;
    if (attempts >= maxAttempts) {
      jdbc.update(
          "UPDATE outbox_event SET attempts=?, last_error=?, quarantined_at=now(), "
              + "next_attempt_at=NULL WHERE id=? AND published_at IS NULL",
          attempts, error, item.id());
      log.warn("Quarantined outbox event {} after {} attempts: {}", item.id(), attempts, error);
    } else {
      jdbc.update(
          "UPDATE outbox_event SET attempts=?, last_error=?, attempted_at=now(), next_attempt_at=? "
              + "WHERE id=? AND published_at IS NULL",
          attempts, error, java.sql.Timestamp.from(Instant.now().plus(retryDelay(attempts))), item.id());
      log.debug("Outbox event {} attempt {} failed, retry scheduled: {}", item.id(), attempts, error);
    }
  }

  /** Exponential backoff for the given attempt number: base * 2^(attempts-1), capped. */
  static Duration retryDelay(Duration base, Duration max, int attempts) {
    if (attempts <= 1) return base;
    Duration delay = base;
    for (int i = 1; i < attempts; i++) {
      delay = delay.plus(delay);
      if (!delay.minus(max).isNegative()) {
        return max;
      }
    }
    return delay;
  }

  Duration retryDelay(int attempts) {
    return retryDelay(backoffBase, backoffMax, attempts);
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
