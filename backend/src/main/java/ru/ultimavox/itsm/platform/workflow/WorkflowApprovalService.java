package ru.ultimavox.itsm.platform.workflow;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.ApprovalMode;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.ApprovalRequirement;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.Transition;

@Service
public class WorkflowApprovalService {
  private final JdbcTemplate jdbc;
  private final WorkflowDefinitionRepository definitions;
  private final WorkflowInstanceRepository instances;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public WorkflowApprovalService(JdbcTemplate jdbc, WorkflowDefinitionRepository definitions,
                                 WorkflowInstanceRepository instances, AuditTrail audit,
                                 IntegrationEventOutbox outbox) {
    this.jdbc = jdbc;
    this.definitions = definitions;
    this.instances = instances;
    this.audit = audit;
    this.outbox = outbox;
  }

  @Transactional
  public ApprovalView request(String objectType, String objectId, String transitionKey, String actor) {
    WorkflowInstance instance = instances.findByObject(objectType, objectId)
        .orElseThrow(() -> new WorkflowTransitionException("Workflow instance not found"));
    WorkflowDefinition definition = definitions.findByObjectKeyAndVersion(objectType, instance.definitionVersion())
        .orElseThrow(() -> new WorkflowTransitionException("Pinned workflow definition not found"));
    Transition transition = definition.findTransition(transitionKey)
        .orElseThrow(() -> new WorkflowTransitionException("Unknown transition: " + transitionKey));
    if (!transition.from().equals(instance.state())) {
      throw new WorkflowTransitionException("Approval transition is not available from current state");
    }
    ApprovalRequirement requirement = transition.approval();
    if (requirement == null) throw new WorkflowTransitionException("Transition does not require approval");

    ApprovalView existing = findOpenForVersion(instance.id(), transitionKey, instance.version());
    if (existing != null) return existing;
    Map<String,String> voters = eligibleVoters(requirement.voterRoles(), actor);
    if (voters.isEmpty()) throw new WorkflowTransitionException("No eligible approval voters");
    if (requirement.mode() == ApprovalMode.QUORUM && requirement.quorum() > voters.size()) {
      throw new WorkflowTransitionException("Approval quorum exceeds eligible voter count");
    }
    UUID requestId = UUID.randomUUID();
    Instant now = Instant.now();
    Integer attempt = jdbc.queryForObject("""
        SELECT COALESCE(MAX(attempt),0)+1 FROM workflow_approval_request
        WHERE org_id=? AND workflow_instance_id=? AND transition_key=? AND source_instance_version=?
        """, Integer.class, OrganizationContext.current(), instance.id(), transitionKey, instance.version());
    try {
      jdbc.update("""
          INSERT INTO workflow_approval_request
            (id,org_id,workflow_instance_id,transition_key,definition_version,
             source_instance_version,attempt,mode,quorum,requested_by,created_at)
          VALUES (?,?,?,?,?,?,?,?,?,?,?)
          """, requestId, OrganizationContext.current(), instance.id(), transitionKey,
          instance.definitionVersion(), instance.version(), attempt, requirement.mode().name(),
          requirement.quorum(), actor, Timestamp.from(now));
      voters.forEach((voter, role) -> jdbc.update("""
          INSERT INTO workflow_approval_vote(request_id,voter_id,voter_role) VALUES (?,?,?)
          """, requestId, voter, role));
    } catch (DuplicateKeyException ex) {
      ApprovalView concurrent = findOpenForVersion(instance.id(), transitionKey, instance.version());
      if (concurrent != null) return concurrent;
      throw ex;
    }
    evidence(actor, "workflow.approval-requested", requestId,
        Map.of("objectType", objectType, "objectId", objectId, "transitionKey", transitionKey), now);
    return find(requestId);
  }

  @Transactional
  public ApprovalView vote(UUID requestId, String voter, Decision decision, String comment) {
    if (decision == null) throw new IllegalArgumentException("decision is required");
    int changed = jdbc.update("""
        UPDATE workflow_approval_vote v SET decision=?,comment=?,decided_at=now()
        FROM workflow_approval_request r
        WHERE v.request_id=r.id AND r.id=? AND r.org_id=? AND r.status='PENDING'
          AND v.voter_id=? AND v.decision='PENDING'
        """, decision.name(), comment, requestId, OrganizationContext.current(), voter);
    if (changed == 0) throw new WorkflowTransitionException("Pending assigned approval vote not found");
    recompute(requestId);
    Instant now = Instant.now();
    evidence(voter, "workflow.approval-voted", requestId,
        Map.of("decision", decision.name()), now);
    return find(requestId);
  }

  public List<ApprovalView> list(String objectType, String objectId) {
    return jdbc.query("""
        SELECT r.id FROM workflow_approval_request r
        JOIN workflow_instance i ON i.id=r.workflow_instance_id
        WHERE r.org_id=? AND i.org_id=? AND i.object_type=? AND i.object_id=?
        ORDER BY r.created_at DESC
        """, (rs, row) -> rs.getObject(1, UUID.class), OrganizationContext.current(),
        OrganizationContext.current(), objectType, objectId).stream().map(this::find).toList();
  }

  UUID requireApproved(WorkflowInstance instance, Transition transition) {
    if (transition.approval() == null) return null;
    List<UUID> ids = jdbc.query("""
        SELECT id FROM workflow_approval_request
        WHERE org_id=? AND workflow_instance_id=? AND transition_key=?
          AND source_instance_version=? AND definition_version=? AND status='APPROVED'
        """, (rs, row) -> rs.getObject(1, UUID.class), OrganizationContext.current(), instance.id(),
        transition.key(), instance.version(), instance.definitionVersion());
    if (ids.isEmpty()) throw new WorkflowTransitionException("Approved transition request is required");
    return ids.getFirst();
  }

  void consume(UUID requestId) {
    if (requestId == null) return;
    int changed = jdbc.update("""
        UPDATE workflow_approval_request SET status='CONSUMED',consumed_at=now()
        WHERE id=? AND org_id=? AND status='APPROVED'
        """, requestId, OrganizationContext.current());
    if (changed != 1) throw new WorkflowTransitionException("Approval was already consumed");
  }

  private Map<String,String> eligibleVoters(Set<String> roles, String requester) {
    Map<String,String> voters = new LinkedHashMap<>();
    jdbc.query("""
        SELECT pr.subject_id,r.role_key FROM principal_role pr JOIN role r ON r.id=pr.role_id
        WHERE pr.org_id=? ORDER BY pr.subject_id,r.role_key
        """, rs -> {
          String subject = rs.getString(1);
          String role = rs.getString(2);
          if (!subject.equals(requester) && roles.contains(role)) voters.putIfAbsent(subject, role);
        }, OrganizationContext.current());
    return voters;
  }

  private void recompute(UUID requestId) {
    ApprovalView view = find(requestId);
    long approved = view.votes().stream().filter(v -> v.decision() == Decision.APPROVED).count();
    long rejected = view.votes().stream().filter(v -> v.decision() == Decision.REJECTED).count();
    long pending = view.votes().stream().filter(v -> v.decision() == null).count();
    boolean pass = switch (view.mode()) {
      case ANY -> approved >= 1;
      case ALL -> approved == view.votes().size();
      case QUORUM -> approved >= view.quorum();
    };
    boolean fail = switch (view.mode()) {
      case ANY -> pending == 0 && approved == 0;
      case ALL -> rejected > 0;
      case QUORUM -> approved + pending < view.quorum();
    };
    if (pass || fail) jdbc.update("""
        UPDATE workflow_approval_request SET status=?,completed_at=now()
        WHERE id=? AND org_id=? AND status='PENDING'
        """, pass ? "APPROVED" : "REJECTED", requestId, OrganizationContext.current());
  }

  private ApprovalView findOpenForVersion(UUID instanceId, String transition, int version) {
    List<UUID> ids = jdbc.query("""
        SELECT id FROM workflow_approval_request
        WHERE org_id=? AND workflow_instance_id=? AND transition_key=? AND source_instance_version=?
          AND status IN ('PENDING','APPROVED') ORDER BY attempt DESC LIMIT 1
        """, (rs, row) -> rs.getObject(1, UUID.class), OrganizationContext.current(), instanceId, transition, version);
    return ids.isEmpty() ? null : find(ids.getFirst());
  }

  private ApprovalView find(UUID id) {
    List<ApprovalView> requests = jdbc.query("""
        SELECT id,transition_key,definition_version,source_instance_version,attempt,mode,quorum,status,
               requested_by,created_at,completed_at,consumed_at
        FROM workflow_approval_request WHERE id=? AND org_id=?
        """, (rs, row) -> new ApprovalView(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3),
        rs.getInt(4), rs.getInt(5), ApprovalMode.valueOf(rs.getString(6)), (Integer)rs.getObject(7),
        Status.valueOf(rs.getString(8)), rs.getString(9), rs.getTimestamp(10).toInstant(),
        instant(rs.getTimestamp(11)), instant(rs.getTimestamp(12)), List.of()),
        id, OrganizationContext.current());
    if (requests.isEmpty()) throw new WorkflowTransitionException("Approval request not found");
    List<VoteView> votes = jdbc.query("""
        SELECT voter_id,voter_role,decision,comment,decided_at FROM workflow_approval_vote
        WHERE request_id=? ORDER BY voter_id
        """, (rs, row) -> new VoteView(rs.getString(1), rs.getString(2),
        "PENDING".equals(rs.getString(3)) ? null : Decision.valueOf(rs.getString(3)),
        rs.getString(4), instant(rs.getTimestamp(5))), id);
    ApprovalView r = requests.getFirst();
    return new ApprovalView(r.id(), r.transitionKey(), r.definitionVersion(), r.sourceInstanceVersion(), r.attempt(),
        r.mode(), r.quorum(), r.status(), r.requestedBy(), r.createdAt(), r.completedAt(), r.consumedAt(), votes);
  }

  private void evidence(String actor, String action, UUID id, Map<String,Object> data, Instant now) {
    UUID correlation = CorrelationContext.currentOrCreate();
    audit.append(new AuditTrail.Entry(actor, action, "workflow-approval", id.toString(),
        Map.of(), data, correlation, now));
    outbox.record(new DomainEvent(UUID.randomUUID(), action, 1, now, correlation,
        "workflow-approval", id.toString(), data));
  }

  private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

  public enum Decision { APPROVED, REJECTED }
  public enum Status { PENDING, APPROVED, REJECTED, CONSUMED }
  public record VoteView(String voterId, String voterRole, Decision decision, String comment, Instant decidedAt) {}
  public record ApprovalView(UUID id, String transitionKey, int definitionVersion, int sourceInstanceVersion, int attempt,
                             ApprovalMode mode, Integer quorum, Status status, String requestedBy,
                             Instant createdAt, Instant completedAt, Instant consumedAt, List<VoteView> votes) {}
}
