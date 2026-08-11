package ru.ultimavox.itsm.platform.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
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
import ru.ultimavox.itsm.platform.workflow.WorkflowApprovalService.Decision;
import ru.ultimavox.itsm.platform.workflow.WorkflowApprovalService.Status;

@Testcontainers
class WorkflowApprovalIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
  static JdbcTemplate jdbc;
  static JdbcWorkflowDefinitionRepository definitions;
  static JdbcWorkflowInstanceRepository instances;
  static WorkflowApprovalService approvals;
  static WorkflowEngine engine;
  static String organization;

  @BeforeAll
  static void setup() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    organization = "approval-test-" + UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("t").header("alg", "none").claim("sub", "admin")
            .claim("organization_id", organization).build()));
    definitions = new JdbcWorkflowDefinitionRepository(jdbc, new ObjectMapper());
    instances = new JdbcWorkflowInstanceRepository(jdbc);
    AuditTrail audit = mock(AuditTrail.class);
    IntegrationEventOutbox outbox = mock(IntegrationEventOutbox.class);
    approvals = new WorkflowApprovalService(jdbc, definitions, instances, audit, outbox);
    engine = new WorkflowEngine(definitions, instances,
        request -> PermissionChecker.Decision.allow("test"), audit, outbox, approvals);
    assign("manager-1", "SERVICE_DESK_MANAGER");
    assign("manager-2", "SERVICE_DESK_MANAGER");
  }

  @AfterAll
  static void clear() { SecurityContextHolder.clearContext(); }

  @Test
  void quorumApprovalGatesTransitionAndIsConsumedExactlyOnce() {
    String objectId = createWorkflow("QUORUM", 2, "quorum-flow");
    WorkflowApprovalService.ApprovalView request = approvals.request(
        "quorum-flow", objectId, "authorize", "initiator");
    assertThat(request.votes()).extracting(WorkflowApprovalService.VoteView::voterId)
        .containsExactly("manager-1", "manager-2").doesNotContain("initiator");

    WorkflowApprovalService.ApprovalView first = approvals.vote(
        request.id(), "manager-1", Decision.APPROVED, "ok");
    assertThat(first.status()).isEqualTo(Status.PENDING);
    assertThatThrownBy(() -> approvals.vote(request.id(), "manager-1", Decision.APPROVED, "again"))
        .isInstanceOf(WorkflowTransitionException.class).hasMessageContaining("Pending assigned");
    assertThatThrownBy(() -> engine.applyTransition(new WorkflowEngine.TransitionCommand(
        "initiator", "quorum-flow", objectId, "authorize", Map.of())))
        .isInstanceOf(WorkflowTransitionException.class).hasMessageContaining("Approved transition request");

    assertThat(approvals.vote(request.id(), "manager-2", Decision.APPROVED, "ok").status())
        .isEqualTo(Status.APPROVED);
    WorkflowInstance transitioned = engine.applyTransition(new WorkflowEngine.TransitionCommand(
        "initiator", "quorum-flow", objectId, "authorize", Map.of()));
    assertThat(transitioned.state()).isEqualTo("APPROVED");
    assertThat(approvals.list("quorum-flow", objectId).getFirst().status()).isEqualTo(Status.CONSUMED);
  }

  @Test
  void anyAndAllModesReachDeterministicTerminalStates() {
    String anyId = createWorkflow("ANY", null, "any-flow");
    var any = approvals.request("any-flow", anyId, "authorize", "initiator");
    assertThat(approvals.vote(any.id(), "manager-1", Decision.APPROVED, null).status())
        .isEqualTo(Status.APPROVED);

    String allId = createWorkflow("ALL", null, "all-flow");
    var all = approvals.request("all-flow", allId, "authorize", "initiator");
    assertThat(approvals.vote(all.id(), "manager-1", Decision.REJECTED, "risk").status())
        .isEqualTo(Status.REJECTED);
    var retry = approvals.request("all-flow", allId, "authorize", "initiator");
    assertThat(retry.attempt()).isEqualTo(2);
    assertThat(approvals.list("all-flow", allId)).hasSize(2);
  }

  private static String createWorkflow(String mode, Integer quorum, String objectType) {
    String objectId = UUID.randomUUID().toString();
    String quorumJson = quorum == null ? "" : ",\"quorum\":" + quorum;
    String definition = """
        {"initialState":"NEW","states":["NEW","APPROVED"],"transitions":[
          {"key":"authorize","from":"NEW","to":"APPROVED","requiredPermissions":[],
           "requiredFields":[],"approval":{"mode":"%s","voterRoles":["SERVICE_DESK_MANAGER"]%s}}
        ]}
        """.formatted(mode, quorumJson);
    jdbc.update("""
        INSERT INTO workflow_definition(org_id,object_key,version,active,definition)
        VALUES (?,?,1,true,?::jsonb)
        """, organization, objectType, definition);
    instances.insert(new WorkflowInstance(UUID.randomUUID(), objectType, objectId, "NEW", 1, 1, Instant.now()));
    return objectId;
  }

  private static void assign(String subject, String roleKey) {
    UUID role = jdbc.queryForObject("SELECT id FROM role WHERE role_key=?", UUID.class, roleKey);
    jdbc.update("INSERT INTO principal_role(org_id,subject_id,role_id) VALUES (?,?,?)",
        organization, subject, role);
  }
}
