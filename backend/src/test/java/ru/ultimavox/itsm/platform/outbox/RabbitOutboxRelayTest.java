package ru.ultimavox.itsm.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

class RabbitOutboxRelayTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final RabbitTemplate rabbit = mock(RabbitTemplate.class);

  private RabbitOutboxRelay relay(int maxAttempts) {
    return new RabbitOutboxRelay(jdbc, rabbit, maxAttempts, Duration.ofSeconds(5), Duration.ofMinutes(5));
  }

  @Test
  void marksRowPublishedAfterSuccessfulBrokerCall() {
    RabbitOutboxRelay.Pending item = pending(0);

    relay(10).publish(item);

    verify(jdbc).update(
        "UPDATE outbox_event SET published_at=now(), last_error=NULL, next_attempt_at=NULL "
            + "WHERE id=? AND published_at IS NULL",
        item.id());
  }

  @Test
  void schedulesRetryWithBackoffOnTransientFailure() {
    RabbitOutboxRelay.Pending item = pending(0);
    doThrow(new AmqpException("broker unreachable"))
        .when(rabbit).convertAndSend(eq(RabbitOutboxConfiguration.EXCHANGE), eq(item.type()),
            any(), any(MessagePostProcessor.class));
    RabbitOutboxRelay relay = relay(10);
    ArgumentCaptor<java.sql.Timestamp> nextAttempt = ArgumentCaptor.forClass(java.sql.Timestamp.class);

    relay.publish(item);

    verify(jdbc).update(
        eq("UPDATE outbox_event SET attempts=?, last_error=?, attempted_at=now(), next_attempt_at=? "
            + "WHERE id=? AND published_at IS NULL"),
        eq(1), eq("broker unreachable"), nextAttempt.capture(), eq(item.id()));
    assertThat(nextAttempt.getValue().toInstant())
        .isAfter(java.time.Instant.now().plus(Duration.ofSeconds(4)));
  }

  @Test
  void quarantinesEventWhenAttemptBudgetIsExhausted() {
    RabbitOutboxRelay.Pending item = pending(9);
    doThrow(new AmqpException("persistent routing failure"))
        .when(rabbit).convertAndSend(eq(RabbitOutboxConfiguration.EXCHANGE), eq(item.type()),
            any(), any(MessagePostProcessor.class));
    RabbitOutboxRelay relay = relay(10);

    relay.publish(item);

    verify(jdbc).update(
        eq("UPDATE outbox_event SET attempts=?, last_error=?, quarantined_at=now(), "
            + "next_attempt_at=NULL WHERE id=? AND published_at IS NULL"),
        eq(10), eq("persistent routing failure"), eq(item.id()));
  }

  @Test
  void backsOffExponentiallyAndCaps() {
    Duration base = Duration.ofSeconds(5);
    Duration max = Duration.ofMinutes(5);
    assertThat(RabbitOutboxRelay.retryDelay(base, max, 1)).isEqualTo(Duration.ofSeconds(5));
    assertThat(RabbitOutboxRelay.retryDelay(base, max, 2)).isEqualTo(Duration.ofSeconds(10));
    assertThat(RabbitOutboxRelay.retryDelay(base, max, 3)).isEqualTo(Duration.ofSeconds(20));
    assertThat(RabbitOutboxRelay.retryDelay(base, max, 7)).isEqualTo(Duration.ofMinutes(5));
    assertThat(RabbitOutboxRelay.retryDelay(base, max, 99)).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void truncatesLongDiagnostics() {
    RabbitOutboxRelay.Pending item = pending(0);
    String longError = "x".repeat(5000);
    doThrow(new AmqpException(longError))
        .when(rabbit).convertAndSend(eq(RabbitOutboxConfiguration.EXCHANGE), eq(item.type()),
            any(), any(MessagePostProcessor.class));
    ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);

    relay(10).publish(item);

    verify(jdbc).update(
        eq("UPDATE outbox_event SET attempts=?, last_error=?, attempted_at=now(), next_attempt_at=? "
            + "WHERE id=? AND published_at IS NULL"),
        eq(1), error.capture(), any(), eq(item.id()));
    assertThat(error.getValue()).hasSize(1000);
  }

  private static RabbitOutboxRelay.Pending pending(int attempts) {
    return new RabbitOutboxRelay.Pending(
        UUID.randomUUID(), "work-item.created", 1, UUID.randomUUID(), null,
        "org-1", "actor-1", "{}", attempts);
  }
}
