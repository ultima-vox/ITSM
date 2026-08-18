package ru.ultimavox.itsm.problemmanagement.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.workflow.WorkflowPolicyGateway;
import ru.ultimavox.itsm.problemmanagement.domain.Problem;
import ru.ultimavox.itsm.servicedesk.WorkItemReferenceQuery;

@Service
public class ProblemCommands {
  private final JdbcTemplate jdbc;
  private final ProblemQuery query;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final WorkflowPolicyGateway workflows;
  private final WorkItemReferenceQuery workItems;
  private final ProblemSearchIndexer searchIndexer;

  public ProblemCommands(
      JdbcTemplate jdbc,
      ProblemQuery query,
      AuditTrail audit,
      IntegrationEventOutbox outbox,
      WorkflowPolicyGateway workflows,
      WorkItemReferenceQuery workItems,
      ProblemSearchIndexer searchIndexer
  ) {
    this.jdbc = jdbc;
    this.query = query;
    this.audit = audit;
    this.outbox = outbox;
    this.workflows = workflows;
    this.workItems = workItems;
    this.searchIndexer = searchIndexer;
  }

  @Transactional
  public Problem create(CreateCommand command, String actor) {
    UUID id = UUID.randomUUID();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    Instant now = Instant.now();
    Long sequence = jdbc.queryForObject("SELECT nextval('problem_number_seq')", Long.class);
    String number = "PRB-%06d".formatted(sequence);
    Problem problem = new Problem(
        id, number, command.title(), Problem.Status.NEW,
        command.rootCause(), command.workaround(), null,
        command.priority(), command.impact(), command.ownerSubject(),
        Set.of(), 0
    );

    jdbc.update(
        """
            INSERT INTO problem (id, org_id, number, title, status, root_cause, workaround, resolution,
                priority, impact, owner_subject, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
        id, OrganizationContext.current(), number, problem.title(), problem.status().name(),
        problem.rootCause(), problem.workaround(), problem.resolution(),
        problem.priority() == null ? null : problem.priority().name(),
        problem.impact() == null ? null : problem.impact().name(),
        problem.ownerSubject(),
        Timestamp.from(now), Timestamp.from(now)
    );
    workflows.startIfDefined("problem", id.toString());

    Map<String, Object> state = Map.of(
        "number", number,
        "title", problem.title(),
        "status", problem.status().name()
    );
    audit.append(new AuditTrail.Entry(
        actor, "problem.created", "problem", id.toString(), Map.of(), state, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "problem.created", 1, now, correlationId, "problem", id.toString(), state
    ));
    searchIndexer.index(problem);
    return problem;
  }

  /** Update RCA fields without changing lifecycle status. */
  @Transactional
  public Problem updateNotes(
      UUID id,
      String rootCause,
      String workaround,
      String resolution,
      Problem.Priority priority,
      Problem.Impact impact,
      String ownerSubject,
      long expectedVersion,
      String actor
  ) {
    Problem current = query.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + id));
    requireVersion(current, expectedVersion);
    Problem withNotes = current.withInvestigationNotes(rootCause, workaround, resolution);
    Problem updated = new Problem(
        withNotes.id(), withNotes.number(), withNotes.title(), withNotes.status(),
        withNotes.rootCause(), withNotes.workaround(), withNotes.resolution(),
        priority != null ? priority : withNotes.priority(),
        impact != null ? impact : withNotes.impact(),
        ownerSubject != null ? ownerSubject : withNotes.ownerSubject(),
        withNotes.linkedWorkItems(), withNotes.version()
    );
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    int changed = jdbc.update(
        """
            UPDATE problem
            SET root_cause = ?, workaround = ?, resolution = ?,
                priority = ?, impact = ?, owner_subject = ?,
                version = version + 1, updated_at = ?
            WHERE id = ? AND org_id = ? AND version = ?
            """,
        updated.rootCause(),
        updated.workaround(),
        updated.resolution(),
        updated.priority() == null ? null : updated.priority().name(),
        updated.impact() == null ? null : updated.impact().name(),
        updated.ownerSubject(),
        Timestamp.from(now),
        id,
        OrganizationContext.current(),
        expectedVersion
    );
    if (changed == 0) throw stale(expectedVersion);
    Map<String, Object> after = Map.of(
        "rootCause", String.valueOf(updated.rootCause()),
        "workaround", String.valueOf(updated.workaround()),
        "resolution", String.valueOf(updated.resolution())
    );
    audit.append(new AuditTrail.Entry(
        actor, "problem.notes-updated", "problem", id.toString(),
        Map.of("status", current.status().name()), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "problem.notes-updated", 1, now, correlationId,
        "problem", id.toString(), after
    ));
    Problem saved = query.findById(id).orElseThrow();
    searchIndexer.index(saved);
    return saved;
  }

  @Transactional
  public Problem transition(
      UUID id,
      Problem.Status target,
      String rootCause,
      String workaround,
      String resolution,
      Long expectedVersion,
      String actor
  ) {
    Problem current = query.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + id));
    long version = expectedVersion == null ? current.version() : expectedVersion;
    requireVersion(current, version);
    Problem updated = current.withInvestigationNotes(rootCause, workaround, resolution).transition(target);
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    Map<String, Object> workflowFields = new HashMap<>();
    if (updated.rootCause() != null) workflowFields.put("root_cause", updated.rootCause());
    if (updated.workaround() != null) workflowFields.put("workaround", updated.workaround());
    if (updated.resolution() != null) workflowFields.put("resolution", updated.resolution());
    workflows.enforceByTarget(
        actor, "problem", id.toString(), current.status().name(), target.name(),
        workflowFields, correlationId
    );

    int changed = jdbc.update(
        """
            UPDATE problem
            SET status = ?, root_cause = ?, workaround = ?, resolution = ?, version = version + 1, updated_at = ?
            WHERE id = ? AND org_id = ? AND version = ?
            """,
        updated.status().name(),
        updated.rootCause(),
        updated.workaround(),
        updated.resolution(),
        Timestamp.from(now),
        id,
        OrganizationContext.current(),
        version
    );
    if (changed == 0) throw stale(version);

    Map<String, Object> before = Map.of("status", current.status().name());
    Map<String, Object> after = Map.of(
        "status", updated.status().name(),
        "rootCause", String.valueOf(updated.rootCause()),
        "workaround", String.valueOf(updated.workaround()),
        "resolution", String.valueOf(updated.resolution())
    );
    audit.append(new AuditTrail.Entry(
        actor, "problem.transitioned", "problem", id.toString(), before, after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "problem.transitioned", 1, now, correlationId, "problem", id.toString(), after
    ));
    Problem saved = query.findById(id).orElseThrow();
    searchIndexer.index(saved);
    return saved;
  }

  private static void requireVersion(Problem current, long expectedVersion) {
    if (expectedVersion < 0 || current.version() != expectedVersion) throw stale(expectedVersion);
  }

  private static OptimisticLockingFailureException stale(long expectedVersion) {
    return new OptimisticLockingFailureException("Problem changed since version " + expectedVersion);
  }

  @Transactional
  public Problem linkWorkItem(UUID problemId, UUID workItemId, String actor) {
    Problem current = query.findById(problemId)
        .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + problemId));

    if (!workItems.exists(workItemId)) {
      throw new IllegalArgumentException("Work item not found: " + workItemId);
    }

    Problem linked = current.linkWorkItem(workItemId);
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();

    jdbc.update(
        """
            INSERT INTO problem_work_item (problem_id, work_item_id, linked_at, linked_by)
            VALUES (?,?,?,?)
            ON CONFLICT DO NOTHING
            """,
        problemId, workItemId, Timestamp.from(now), actor
    );

    Map<String, Object> after = Map.of(
        "workItemId", workItemId.toString(),
        "linkedWorkItems", linked.linkedWorkItems().stream().map(UUID::toString).toList()
    );
    audit.append(new AuditTrail.Entry(
        actor, "problem.work-item-linked", "problem", problemId.toString(),
        Map.of(), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "problem.work-item-linked", 1, now, correlationId,
        "problem", problemId.toString(), after
    ));
    return linked;
  }

  public record CreateCommand(String title, String rootCause, String workaround,
      Problem.Priority priority, Problem.Impact impact, String ownerSubject) {}
}
