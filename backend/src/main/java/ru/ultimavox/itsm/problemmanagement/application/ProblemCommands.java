package ru.ultimavox.itsm.problemmanagement.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.problemmanagement.domain.Problem;

@Service
public class ProblemCommands {
  private final JdbcTemplate jdbc;
  private final ProblemQuery query;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public ProblemCommands(JdbcTemplate jdbc, ProblemQuery query, AuditTrail audit, IntegrationEventOutbox outbox) {
    this.jdbc = jdbc;
    this.query = query;
    this.audit = audit;
    this.outbox = outbox;
  }

  @Transactional
  public Problem create(CreateCommand command, String actor) {
    UUID id = UUID.randomUUID();
    UUID correlationId = UUID.randomUUID();
    Instant now = Instant.now();
    Long sequence = jdbc.queryForObject("SELECT nextval('problem_number_seq')", Long.class);
    String number = "PRB-%06d".formatted(sequence);
    Problem problem = new Problem(
        id, number, command.title(), Problem.Status.NEW,
        command.rootCause(), command.workaround(), Set.of()
    );

    jdbc.update(
        """
            INSERT INTO problem (id, number, title, status, root_cause, workaround, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?)
            """,
        id, number, problem.title(), problem.status().name(), problem.rootCause(), problem.workaround(),
        Timestamp.from(now), Timestamp.from(now)
    );

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
    return problem;
  }

  /** Update RCA fields without changing lifecycle status. */
  @Transactional
  public Problem updateNotes(
      UUID id,
      String rootCause,
      String workaround,
      String resolution,
      String actor
  ) {
    Problem current = query.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + id));
    Problem updated = current.withInvestigationNotes(rootCause, workaround, resolution);
    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();
    jdbc.update(
        """
            UPDATE problem
            SET root_cause = ?, workaround = ?, resolution = ?, updated_at = ?
            WHERE id = ?
            """,
        updated.rootCause(),
        updated.workaround(),
        updated.resolution(),
        Timestamp.from(now),
        id
    );
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
    return updated;
  }

  @Transactional
  public Problem transition(
      UUID id,
      Problem.Status target,
      String rootCause,
      String workaround,
      String resolution,
      String actor
  ) {
    Problem current = query.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + id));
    Problem updated = current.withInvestigationNotes(rootCause, workaround, resolution).transition(target);
    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();

    jdbc.update(
        """
            UPDATE problem
            SET status = ?, root_cause = ?, workaround = ?, resolution = ?, updated_at = ?
            WHERE id = ?
            """,
        updated.status().name(),
        updated.rootCause(),
        updated.workaround(),
        updated.resolution(),
        Timestamp.from(now),
        id
    );

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
    return updated;
  }

  @Transactional
  public Problem linkWorkItem(UUID problemId, UUID workItemId, String actor) {
    Problem current = query.findById(problemId)
        .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + problemId));

    Integer exists = jdbc.queryForObject(
        "SELECT COUNT(*) FROM work_item WHERE id = ?",
        Integer.class,
        workItemId
    );
    if (exists == null || exists == 0) {
      throw new IllegalArgumentException("Work item not found: " + workItemId);
    }

    Problem linked = current.linkWorkItem(workItemId);
    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();

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

  public record CreateCommand(String title, String rootCause, String workaround) {}
}
