package ru.ultimavox.itsm.servicecatalog.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;

@Service
public class CatalogFulfillmentService {
  private final JdbcTemplate jdbc;
  private final AuditTrail audit;

  CatalogFulfillmentService(JdbcTemplate jdbc, AuditTrail audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  public List<ApprovalView> approvals(UUID requestId) {
    return jdbc.query("""
        SELECT id, approver_role, state, decided_by, decided_at, comment, created_at
        FROM catalog_request_approval WHERE org_id=? AND request_id=? ORDER BY created_at
        """, (rs, row) -> new ApprovalView(rs.getObject("id", UUID.class), rs.getString("approver_role"),
        rs.getString("state"), rs.getString("decided_by"), instant(rs.getTimestamp("decided_at")),
        rs.getString("comment"), rs.getTimestamp("created_at").toInstant()),
        OrganizationContext.current(), requestId);
  }

  public List<TaskView> tasks(UUID requestId) {
    return jdbc.query("""
        SELECT id, title, state, assignee_id, created_at, completed_at
        FROM catalog_fulfillment_task WHERE org_id=? AND request_id=? ORDER BY created_at
        """, (rs, row) -> new TaskView(rs.getObject("id", UUID.class), rs.getString("title"),
        rs.getString("state"), rs.getString("assignee_id"), rs.getTimestamp("created_at").toInstant(),
        instant(rs.getTimestamp("completed_at"))), OrganizationContext.current(), requestId);
  }

  @Transactional
  public ApprovalView decide(UUID requestId, UUID approvalId, Decision decision, String comment, String actor) {
    ApprovalView current = requireApproval(requestId, approvalId);
    if (!"PENDING".equals(current.state())) throw new IllegalStateException("Approval already decided");
    Instant now = Instant.now();
    String state = decision.name();
    int changed = jdbc.update("""
        UPDATE catalog_request_approval SET state=?,decided_by=?,decided_at=?,comment=?
        WHERE id=? AND request_id=? AND org_id=? AND state='PENDING'
        """, state, actor, Timestamp.from(now), normalize(comment), approvalId, requestId,
        OrganizationContext.current());
    if (changed != 1) throw new IllegalStateException("Approval changed concurrently");
    if (decision == Decision.APPROVED) {
      String itemKey = jdbc.queryForObject("""
          SELECT ci.item_key FROM catalog_request cr JOIN catalog_item ci ON ci.id=cr.catalog_item_id
          WHERE cr.id=? AND cr.org_id=?
          """, String.class, requestId, OrganizationContext.current());
      jdbc.update("UPDATE catalog_request SET status='FULFILLING',updated_at=? WHERE id=? AND org_id=?",
          Timestamp.from(now), requestId, OrganizationContext.current());
      jdbc.update("INSERT INTO catalog_fulfillment_task(id,org_id,request_id,title,state,created_at) VALUES (?,?,?,?,?,?)",
          UUID.randomUUID(), OrganizationContext.current(), requestId, "Fulfill " + itemKey, "OPEN", Timestamp.from(now));
    } else {
      jdbc.update("UPDATE catalog_request SET status='REJECTED',updated_at=?,completed_at=? WHERE id=? AND org_id=?",
          Timestamp.from(now), Timestamp.from(now), requestId, OrganizationContext.current());
    }
    recordAudit(actor, "catalog-request.approval-" + state.toLowerCase(), requestId,
        Map.of("approvalId", approvalId.toString(), "state", state), now);
    return new ApprovalView(approvalId, current.approverRole(), state, actor, now, normalize(comment), current.createdAt());
  }

  @Transactional
  public TaskView updateTask(UUID requestId, UUID taskId, TaskState state, String assigneeId, String actor) {
    TaskView current = requireTask(requestId, taskId);
    if ("COMPLETED".equals(current.state()) || "CANCELLED".equals(current.state())) {
      throw new IllegalStateException("Terminal task cannot be changed");
    }
    Instant now = Instant.now();
    Timestamp completed = state == TaskState.COMPLETED ? Timestamp.from(now) : null;
    int changed = jdbc.update("""
        UPDATE catalog_fulfillment_task SET state=?,assignee_id=?,completed_at=?
        WHERE id=? AND request_id=? AND org_id=? AND state NOT IN ('COMPLETED','CANCELLED')
        """, state.name(), normalize(assigneeId), completed, taskId, requestId, OrganizationContext.current());
    if (changed != 1) throw new IllegalStateException("Fulfillment task changed concurrently");
    if (state == TaskState.COMPLETED) {
      Integer open = jdbc.queryForObject("SELECT count(*) FROM catalog_fulfillment_task WHERE request_id=? AND org_id=? AND state NOT IN ('COMPLETED','CANCELLED')",
          Integer.class, requestId, OrganizationContext.current());
      if (open != null && open == 0) {
        jdbc.update("UPDATE catalog_request SET status='COMPLETED',updated_at=?,completed_at=? WHERE id=? AND org_id=?",
            Timestamp.from(now), Timestamp.from(now), requestId, OrganizationContext.current());
      }
    }
    recordAudit(actor, "catalog-request.task-" + state.name().toLowerCase(), requestId,
        Map.of("taskId", taskId.toString(), "state", state.name()), now);
    return new TaskView(taskId, current.title(), state.name(), normalize(assigneeId), current.createdAt(),
        completed == null ? null : now);
  }

  private ApprovalView requireApproval(UUID requestId, UUID approvalId) {
    return approvals(requestId).stream().filter(a -> a.id().equals(approvalId)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Approval not found"));
  }
  private TaskView requireTask(UUID requestId, UUID taskId) {
    return tasks(requestId).stream().filter(t -> t.id().equals(taskId)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Fulfillment task not found"));
  }
  private void recordAudit(String actor, String action, UUID requestId, Map<String,Object> after, Instant now) {
    audit.append(new AuditTrail.Entry(actor, action, "catalog-request", requestId.toString(), Map.of(), after,
        CorrelationContext.currentOrCreate(), now));
  }
  private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

  public enum Decision { APPROVED, REJECTED }
  public enum TaskState { IN_PROGRESS, COMPLETED, CANCELLED }
  public record ApprovalView(UUID id, String approverRole, String state, String decidedBy,
                             Instant decidedAt, String comment, Instant createdAt) {}
  public record TaskView(UUID id, String title, String state, String assigneeId,
                         Instant createdAt, Instant completedAt) {}
}
