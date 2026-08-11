package ru.ultimavox.itsm.platform.workflow;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.audit.AuditTrail;

/** Durable, tenant-scoped timer store. Database leases make polling safe across replicas. */
@Service
public class WorkflowTimerService {
  private final JdbcTemplate jdbc;
  private final AuditTrail audit;

  @Autowired
  public WorkflowTimerService(JdbcTemplate jdbc, AuditTrail audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  WorkflowTimerService(JdbcTemplate jdbc) {
    this(jdbc, entry -> {});
  }

  @Transactional
  public void replaceForState(WorkflowInstance instance, WorkflowDefinition definition) {
    String org = OrganizationContext.current();
    jdbc.update("""
        UPDATE workflow_timer SET status='CANCELLED',locked_until=NULL,updated_at=now()
        WHERE org_id=? AND workflow_instance_id=? AND status IN ('PENDING','RETRY')
        """, org, instance.id());
    Instant now = Instant.now();
    definition.transitionsFrom(instance.state()).stream()
        .filter(transition -> transition.timer() != null)
        .forEach(transition -> {
          Instant dueAt = now.plusSeconds(transition.timer().delaySeconds());
          int inserted = jdbc.update("""
            INSERT INTO workflow_timer
              (id,org_id,workflow_instance_id,object_type,object_id,transition_key,
               definition_version,source_instance_version,due_at,max_attempts)
            VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
            """, UUID.randomUUID(), org, instance.id(), instance.objectType(), instance.objectId(),
            transition.key(), instance.definitionVersion(), instance.version(),
            Timestamp.from(dueAt), transition.timer().maxAttempts());
          if (inserted == 1) appendAudit("workflow.timer-scheduled", instance.objectType(), instance.objectId(),
              java.util.Map.of("transitionKey", transition.key(), "dueAt", dueAt.toString(),
                  "definitionVersion", instance.definitionVersion(), "instanceVersion", instance.version()));
        });
  }

  @Transactional
  public List<Timer> claimDue(int batchSize, long leaseSeconds) {
    return jdbc.query("""
        WITH due AS (
          SELECT id FROM workflow_timer
          WHERE (status IN ('PENDING','RETRY') AND due_at<=now())
             OR (status='PROCESSING' AND locked_until<now())
          ORDER BY due_at,id
          LIMIT ? FOR UPDATE SKIP LOCKED
        )
        UPDATE workflow_timer t
        SET status='PROCESSING',attempts=attempts+1,
            locked_until=now()+(? * interval '1 second'),updated_at=now()
        FROM due WHERE t.id=due.id
        RETURNING t.id,t.org_id,t.workflow_instance_id,t.object_type,t.object_id,
                  t.transition_key,t.definition_version,t.source_instance_version,
                  t.due_at,t.status,t.attempts,t.max_attempts,t.last_error
        """, (rs, row) -> map(rs), batchSize, leaseSeconds);
  }

  @Transactional
  public void complete(UUID id, String org) {
    jdbc.update("""
        UPDATE workflow_timer SET status='COMPLETED',locked_until=NULL,last_error=NULL,
          completed_at=now(),updated_at=now() WHERE id=? AND org_id=? AND status='PROCESSING'
        """, id, org);
  }

  @Transactional
  public void cancel(UUID id, String org, String reason) {
    int changed = jdbc.update("""
        UPDATE workflow_timer SET status='CANCELLED',locked_until=NULL,last_error=?,updated_at=now()
        WHERE id=? AND org_id=? AND status='PROCESSING'
        """, truncate(reason), id, org);
    if (changed == 1) OrganizationContext.runAs(org, () -> {
      appendAudit("workflow.timer-cancelled", "workflow_timer", id.toString(),
          java.util.Map.of("reason", truncate(reason)));
      return null;
    });
  }

  @Transactional
  public void retryOrDead(Timer timer, Throwable failure) {
    boolean dead = timer.attempts() >= timer.maxAttempts();
    long backoff = Math.min(3600, 1L << Math.min(12, Math.max(0, timer.attempts() - 1)));
    jdbc.update("""
        UPDATE workflow_timer
        SET status=?,due_at=CASE WHEN ? THEN due_at ELSE now()+(? * interval '1 second') END,
            locked_until=NULL,last_error=?,updated_at=now()
        WHERE id=? AND org_id=? AND status='PROCESSING'
        """, dead ? "DEAD" : "RETRY", dead, backoff, truncate(failure.getMessage()), timer.id(), timer.orgId());
    if (dead) OrganizationContext.runAs(timer.orgId(), () -> {
      appendAudit("workflow.timer-dead", timer.objectType(), timer.objectId(),
          java.util.Map.of("timerId", timer.id().toString(), "transitionKey", timer.transitionKey(),
              "attempts", timer.attempts(), "error", truncate(failure.getMessage())));
      return null;
    });
  }

  @Transactional(readOnly = true)
  public List<Timer> list(String objectType, String objectId) {
    return jdbc.query("""
        SELECT t.id,t.org_id,t.workflow_instance_id,t.object_type,t.object_id,t.transition_key,
          t.definition_version,t.source_instance_version,t.due_at,t.status,t.attempts,t.max_attempts,t.last_error
        FROM workflow_timer t JOIN workflow_instance i ON i.id=t.workflow_instance_id
        WHERE t.org_id=? AND i.org_id=? AND t.object_type=? AND t.object_id=?
        ORDER BY t.created_at DESC
        """, (rs, row) -> map(rs), OrganizationContext.current(), OrganizationContext.current(), objectType, objectId);
  }

  private Timer map(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new Timer(rs.getObject("id", UUID.class), rs.getString("org_id"),
        rs.getObject("workflow_instance_id", UUID.class), rs.getString("object_type"),
        rs.getString("object_id"), rs.getString("transition_key"), rs.getInt("definition_version"),
        rs.getInt("source_instance_version"), rs.getTimestamp("due_at").toInstant(),
        rs.getString("status"), rs.getInt("attempts"), rs.getInt("max_attempts"),
        rs.getString("last_error"));
  }

  private String truncate(String value) {
    if (value == null || value.isBlank()) return "Unknown timer execution error";
    return value.substring(0, Math.min(value.length(), 2000));
  }

  private void appendAudit(String action, String objectType, String objectId, java.util.Map<String, Object> after) {
    audit.append(new AuditTrail.Entry("system:workflow-timer", action, objectType, objectId,
        java.util.Map.of(), after, UUID.randomUUID(), Instant.now()));
  }

  public record Timer(UUID id, String orgId, UUID workflowInstanceId, String objectType,
      String objectId, String transitionKey, int definitionVersion, int sourceInstanceVersion,
      Instant dueAt, String status, int attempts, int maxAttempts, String lastError) {}
}
