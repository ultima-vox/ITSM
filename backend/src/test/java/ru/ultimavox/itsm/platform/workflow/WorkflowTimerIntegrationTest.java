package ru.ultimavox.itsm.platform.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.PermissionChecker;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Testcontainers(disabledWithoutDocker = true)
class WorkflowTimerIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
  static JdbcTemplate jdbc;
  static JdbcWorkflowDefinitionRepository definitions;
  static JdbcWorkflowInstanceRepository instances;
  static WorkflowTimerService timers;
  static WorkflowTimerProcessor processor;
  static String organization;

  @BeforeAll
  static void setup() {
    var dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    organization = "timer-test-" + UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("t").header("alg", "none").claim("sub", "admin")
            .claim("organization_id", organization).build()));
    definitions = new JdbcWorkflowDefinitionRepository(jdbc, new ObjectMapper());
    instances = new JdbcWorkflowInstanceRepository(jdbc);
    timers = new WorkflowTimerService(jdbc);
    AuditTrail audit = mock(AuditTrail.class);
    IntegrationEventOutbox outbox = mock(IntegrationEventOutbox.class);
    WorkflowEngine engine = new WorkflowEngine(definitions, instances,
        request -> PermissionChecker.Decision.allow("test"), audit, outbox, null, timers);
    processor = new WorkflowTimerProcessor(engine);
  }

  @AfterAll
  static void clear() { SecurityContextHolder.clearContext(); }

  @Test
  void claimsOnceAndExecutesVersionPinnedTransition() {
    String objectType = createDefinition("timer-complete", 3);
    WorkflowInstance instance = start(objectType);
    jdbc.update("UPDATE workflow_timer SET due_at=now()-interval '1 second' WHERE workflow_instance_id=?",
        instance.id());

    var claimed = timers.claimDue(10, 60);
    assertThat(claimed).hasSize(1);
    assertThat(timers.claimDue(10, 60)).isEmpty();
    var timer = claimed.getFirst();
    assertThat(processor.execute(timer)).isEqualTo(WorkflowTimerProcessor.Result.COMPLETED);
    timers.complete(timer.id(), timer.orgId());

    assertThat(instances.findByObject(objectType, instance.objectId()).orElseThrow().state()).isEqualTo("DONE");
    assertThat(timers.list(objectType, instance.objectId()).getFirst().status()).isEqualTo("COMPLETED");
  }

  @Test
  void staleTimerCancelsAndFailureBecomesDeadAfterBoundedAttempts() {
    String staleType = createDefinition("timer-stale", 2);
    WorkflowInstance stale = start(staleType);
    jdbc.update("UPDATE workflow_timer SET due_at=now()-interval '1 second' WHERE workflow_instance_id=?", stale.id());
    var staleTimer = timers.claimDue(10, 60).getFirst();
    jdbc.update("UPDATE workflow_instance SET version=version+1 WHERE id=?", stale.id());
    assertThat(processor.execute(staleTimer)).isEqualTo(WorkflowTimerProcessor.Result.STALE);
    timers.cancel(staleTimer.id(), staleTimer.orgId(), "stale");
    assertThat(timers.list(staleType, stale.objectId()).getFirst().status()).isEqualTo("CANCELLED");

    String retryType = createDefinition("timer-retry", 2);
    WorkflowInstance retry = start(retryType);
    jdbc.update("UPDATE workflow_timer SET due_at=now()-interval '1 second' WHERE workflow_instance_id=?", retry.id());
    var first = timers.claimDue(10, 60).getFirst();
    timers.retryOrDead(first, new IllegalStateException("temporary"));
    jdbc.update("UPDATE workflow_timer SET due_at=now()-interval '1 second' WHERE id=?", first.id());
    var second = timers.claimDue(10, 60).getFirst();
    timers.retryOrDead(second, new IllegalStateException("permanent"));
    var dead = timers.list(retryType, retry.objectId()).getFirst();
    assertThat(dead.status()).isEqualTo("DEAD");
    assertThat(dead.attempts()).isEqualTo(2);
    assertThat(dead.lastError()).isEqualTo("permanent");
  }

  private static String createDefinition(String key, int maxAttempts) {
    String objectType = key + "-" + UUID.randomUUID();
    String definition = """
        {"initialState":"WAITING","states":["WAITING","DONE"],"transitions":[
          {"key":"expire","from":"WAITING","to":"DONE","requiredPermissions":[],
           "requiredFields":[],"timer":{"delaySeconds":1,"maxAttempts":%d}}
        ]}
        """.formatted(maxAttempts);
    jdbc.update("""
        INSERT INTO workflow_definition(org_id,object_key,version,active,definition)
        VALUES (?,?,1,true,?::jsonb)
        """, organization, objectType, definition);
    return objectType;
  }

  private static WorkflowInstance start(String objectType) {
    WorkflowDefinition definition = definitions.findActiveByObjectKey(objectType).orElseThrow();
    WorkflowInstance instance = new WorkflowInstance(UUID.randomUUID(), objectType,
        UUID.randomUUID().toString(), "WAITING", 1, 1, Instant.now());
    WorkflowInstance saved = instances.insert(instance);
    timers.replaceForState(saved, definition);
    return saved;
  }
}
