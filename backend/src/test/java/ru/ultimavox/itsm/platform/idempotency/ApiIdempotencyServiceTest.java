package ru.ultimavox.itsm.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ApiIdempotencyServiceTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static ApiIdempotencyService service;
  static TransactionTemplate transactions;

  @BeforeAll
  static void migrate() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
    service = new ApiIdempotencyService(new JdbcTemplate(dataSource), new ObjectMapper());
    transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  @Test
  void replaysCompletedResponseWithoutExecutingActionAgain() {
    String key = UUID.randomUUID().toString();
    AtomicInteger calls = new AtomicInteger();
    Request request = new Request("Printer broken", Map.of("floor", 4, "room", "401"));

    ApiIdempotencyService.Result<Response> first = transactions.execute(status -> service.execute(
        key, "test.create", "alice", request, Response.class,
        () -> new Response(UUID.randomUUID(), calls.incrementAndGet())));
    ApiIdempotencyService.Result<Response> replay = transactions.execute(status -> service.execute(
        key, "test.create", "alice", request, Response.class,
        () -> new Response(UUID.randomUUID(), calls.incrementAndGet())));

    assertThat(first).isNotNull();
    assertThat(replay).isNotNull();
    assertThat(first.replayed()).isFalse();
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.value()).isEqualTo(first.value());
    assertThat(calls).hasValue(1);
  }

  @Test
  void rejectsSameKeyWithDifferentPayload() {
    String key = UUID.randomUUID().toString();
    transactions.executeWithoutResult(status -> service.execute(
        key, "test.create", "alice", new Request("one", Map.of()), Response.class,
        () -> new Response(UUID.randomUUID(), 1)));

    assertThatThrownBy(() -> transactions.executeWithoutResult(status -> service.execute(
        key, "test.create", "alice", new Request("two", Map.of()), Response.class,
        () -> new Response(UUID.randomUUID(), 2))))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessageContaining("different request");
  }

  @Test
  void failedBusinessTransactionDoesNotConsumeKey() {
    String key = UUID.randomUUID().toString();
    assertThatThrownBy(() -> transactions.executeWithoutResult(status -> service.execute(
        key, "test.create", "alice", new Request("retry", Map.of()), Response.class,
        () -> { throw new IllegalStateException("business write failed"); })))
        .isInstanceOf(IllegalStateException.class);

    ApiIdempotencyService.Result<Response> retry = transactions.execute(status -> service.execute(
        key, "test.create", "alice", new Request("retry", Map.of()), Response.class,
        () -> new Response(UUID.randomUUID(), 1)));
    assertThat(retry).isNotNull();
    assertThat(retry.replayed()).isFalse();
  }

  record Request(String title, Map<String, Object> fields) {}
  record Response(UUID id, int sequence) {}
}
