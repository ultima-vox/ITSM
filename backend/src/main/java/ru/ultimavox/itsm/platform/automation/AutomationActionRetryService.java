package ru.ultimavox.itsm.platform.automation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;

/**
 * Retry engine for failed automation actions.
 *
 * <p>When an action fails, {@link AutomationRunner} schedules a retry row here holding a snapshot
 * of the triggering {@link DomainEvent} and the action parameters. A scheduled sweep re-drives due
 * rows with exponential backoff; rows that exhaust the attempt budget are quarantined
 * ({@code quarantined_at}) and stop being polled so a poison action can never loop forever.
 * A successful retry rewrites the action-log row to {@code SUCCEEDED} so the execution history
 * shows the eventual outcome.
 */
@Service
public class AutomationActionRetryService {

  private static final Logger log = LoggerFactory.getLogger(AutomationActionRetryService.class);
  private static final TypeReference<DomainEvent> EVENT = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final AllowlistedActionExecutor executor;
  private final AutomationActionLogRepository actionLog;
  private final int maxAttempts;
  private final Duration backoffBase;
  private final Duration backoffMax;

  public AutomationActionRetryService(
      JdbcTemplate jdbc,
      ObjectMapper json,
      AllowlistedActionExecutor executor,
      AutomationActionLogRepository actionLog,
      @Value("${itsm.automation.max-attempts:5}") int maxAttempts,
      @Value("${itsm.automation.backoff-base:PT30S}") Duration backoffBase,
      @Value("${itsm.automation.backoff-max:PT10M}") Duration backoffMax) {
    this.jdbc = jdbc;
    this.json = json;
    this.executor = executor;
    this.actionLog = actionLog;
    this.maxAttempts = maxAttempts;
    this.backoffBase = backoffBase;
    this.backoffMax = backoffMax;
  }

  /**
   * Records a retry for a failed action. Idempotent per (organization, rule, event, action);
   * an existing retry row is never reset. The row starts at one attempt because the inline
   * execution that just failed already consumed one, so {@code max-attempts} bounds the total
   * number of executions rather than the retries alone.
   */
  public void schedule(DomainEvent event, String ruleKey, String actionType, Map<String, Object> parameters, String error) {
    try {
      jdbc.update("""
              INSERT INTO automation_action_retry
                (org_id, rule_key, event_id, action_type, attempts, next_attempt_at, last_error, event_json, action_parameters)
              VALUES (?, ?, ?, ?, 1, ?, ?, ?::jsonb, ?::jsonb)
              ON CONFLICT (org_id, rule_key, event_id, action_type) DO NOTHING
              """,
          OrganizationContext.current(),
          ruleKey,
          event.id(),
          actionType,
          Timestamp.from(Instant.now().plus(retryDelay(1))),
          truncate(error),
          json.writeValueAsString(event),
          json.writeValueAsString(parameters == null ? Map.of() : parameters));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize automation action retry", ex);
    }
  }

  /**
   * Re-drives due, non-quarantined retries. Returns the number of retry attempts processed.
   * Each row is executed in its own organization scope.
   */
  @Transactional
  public int retryDue(int limit) {
    int cap = Math.min(Math.max(limit, 1), 200);
    List<RetryAttempt> due = jdbc.query("""
            SELECT id, org_id, rule_key, event_id, action_type, attempts, event_json::text, action_parameters::text
            FROM automation_action_retry
            WHERE quarantined_at IS NULL AND next_attempt_at <= now()
            ORDER BY next_attempt_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """,
        (rs, row) -> new RetryAttempt(
            rs.getObject("id", UUID.class),
            rs.getString("org_id"),
            rs.getString("rule_key"),
            rs.getObject("event_id", UUID.class),
            rs.getString("action_type"),
            rs.getInt("attempts"),
            rs.getString("event_json"),
            rs.getString("action_parameters")),
        cap);
    for (RetryAttempt item : due) {
      OrganizationContext.runAs(item.organizationId(), () -> {
        retryOne(item);
        return null;
      });
    }
    return due.size();
  }

  private void retryOne(RetryAttempt item) {
    try {
      DomainEvent event = json.readValue(item.eventJson(), EVENT);
      Map<String, Object> parameters = json.readValue(item.actionParameters(), MAP);
      executor.execute(new AutomationRule.Action(item.actionType(), parameters), event);
      int attempts = item.attempts() + 1;
      actionLog.complete(item.ruleKey(), item.eventId(), item.actionType(), "SUCCEEDED",
          Map.of("eventType", event.type(), "aggregateId", event.aggregateId(), "retried", true), attempts);
      jdbc.update("DELETE FROM automation_action_retry WHERE id = ?", item.id());
      log.info("Automation retry succeeded rule={} action={} event={} attempt={}",
          item.ruleKey(), item.actionType(), item.eventId(), attempts);
    } catch (Exception failure) {
      String error = truncate(safeMessage(failure));
      int attempts = item.attempts() + 1;
      if (attempts >= maxAttempts) {
        jdbc.update("""
                UPDATE automation_action_retry
                SET attempts = ?, last_error = ?, quarantined_at = now()
                WHERE id = ?
                """,
            attempts, error, item.id());
        log.warn("Quarantined automation retry rule={} action={} event={} after {} attempts: {}",
            item.ruleKey(), item.actionType(), item.eventId(), attempts, error);
      } else {
        jdbc.update("""
                UPDATE automation_action_retry
                SET attempts = ?, last_error = ?, next_attempt_at = ?
                WHERE id = ?
                """,
            attempts, error, Timestamp.from(Instant.now().plus(retryDelay(attempts))), item.id());
        log.debug("Automation retry rule={} action={} event={} attempt {} failed, retry scheduled",
            item.ruleKey(), item.actionType(), item.eventId(), attempts);
      }
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

  private static String safeMessage(Exception failure) {
    String message = failure.getMessage();
    if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
    return message.length() <= 500 ? message : message.substring(0, 500);
  }

  private static String truncate(String message) {
    return message == null ? "Unknown automation error" : message.substring(0, Math.min(message.length(), 1000));
  }

  record RetryAttempt(
      UUID id,
      String organizationId,
      String ruleKey,
      UUID eventId,
      String actionType,
      int attempts,
      String eventJson,
      String actionParameters
  ) {}
}
