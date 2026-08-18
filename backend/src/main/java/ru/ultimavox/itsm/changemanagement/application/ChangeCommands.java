package ru.ultimavox.itsm.changemanagement.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.changemanagement.domain.Change;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.workflow.WorkflowPolicyGateway;

@Service
public class ChangeCommands {
  private final JdbcTemplate jdbc;
  private final ChangeQuery query;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final WorkflowPolicyGateway workflows;
  private final ChangeSearchIndexer searchIndexer;

  public ChangeCommands(
      JdbcTemplate jdbc,
      ChangeQuery query,
      AuditTrail audit,
      IntegrationEventOutbox outbox,
      WorkflowPolicyGateway workflows,
      ChangeSearchIndexer searchIndexer
  ) {
    this.jdbc = jdbc;
    this.query = query;
    this.audit = audit;
    this.outbox = outbox;
    this.workflows = workflows;
    this.searchIndexer = searchIndexer;
  }

  @Transactional
  public Change create(CreateCommand command, String actor) {
    UUID id = UUID.randomUUID();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    Instant now = Instant.now();
    Long sequence = jdbc.queryForObject("SELECT nextval('change_number_seq')", Long.class);
    String number = "CHG-%06d".formatted(sequence);

    Change change = new Change(
        id,
        number,
        command.type(),
        command.risk(),
        Change.Status.DRAFT,
        command.title(),
        command.plannedStart(),
        command.plannedEnd(),
        command.implementationPlan(),
        command.rollbackPlan(),
        command.businessJustification(),
        command.cabNotes(),
        command.cabRiskLevel()
    );

    jdbc.update(
        """
            INSERT INTO change_request (
              id, org_id, number, type, risk, status, title, planned_start, planned_end,
              implementation_plan, rollback_plan, test_plan, requester_id, created_at, updated_at,
              business_justification, cab_notes, cab_risk_level, impact
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
        id, OrganizationContext.current(), number, change.type().name(), change.risk().name(), change.status().name(), change.title(),
        change.plannedStart() == null ? null : java.sql.Timestamp.from(change.plannedStart()),
        change.plannedEnd() == null ? null : java.sql.Timestamp.from(change.plannedEnd()),
        change.implementationPlan(), change.rollbackPlan(), change.testPlan(), actor,
        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now),
        change.businessJustification(), change.cabNotes(),
        change.cabRiskLevel() == null ? null : change.cabRiskLevel().name(),
        change.impact() == null ? null : change.impact().name()
    );
    workflows.startIfDefined("change", id.toString());

    Map<String, Object> state = Map.of(
        "number", number,
        "type", change.type().name(),
        "risk", change.risk().name(),
        "status", change.status().name(),
        "title", change.title()
    );
    audit.append(new AuditTrail.Entry(
        actor, "change.created", "change", id.toString(), Map.of(), state, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "change.created", 1, now, correlationId, "change", id.toString(), state
    ));
    searchIndexer.index(change);
    return change;
  }

  @Transactional
  public Change transition(UUID id, Change.Status target, String cabNotes, Change.Risk cabRiskLevel,
                           Long expectedVersion, String actor) {
    Change current = query.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Change not found: " + id));
    long version = expectedVersion == null ? current.version() : expectedVersion;
    if (version < 0 || current.version() != version) {
      throw new OptimisticLockingFailureException("Change changed since version " + version);
    }

    Change withCab = cabNotes != null || cabRiskLevel != null
        ? current.withCabAssessment(
            cabNotes != null ? cabNotes : current.cabNotes(),
            cabRiskLevel != null ? cabRiskLevel : current.cabRiskLevel()
        )
        : current;
    Change updated = withCab.transition(target);
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    Map<String, Object> workflowFields = new HashMap<>();
    if (updated.cabNotes() != null) workflowFields.put("cab_notes", updated.cabNotes());
    if (updated.cabRiskLevel() != null) workflowFields.put("cab_risk_level", updated.cabRiskLevel().name());
    workflows.enforceByTarget(
        actor, "change", id.toString(), current.status().name(), target.name(),
        workflowFields, correlationId
    );

    int changed = jdbc.update(
        """
            UPDATE change_request
            SET status = ?, cab_notes = ?, cab_risk_level = ?, version = version + 1, updated_at = ?
            WHERE id = ? AND org_id = ? AND version = ?
            """,
        updated.status().name(),
        updated.cabNotes(),
        updated.cabRiskLevel() == null ? null : updated.cabRiskLevel().name(),
        java.sql.Timestamp.from(now),
        id,
        OrganizationContext.current(),
        version
    );
    if (changed == 0) throw new OptimisticLockingFailureException("Change changed since version " + version);

    if (target == Change.Status.APPROVED || target == Change.Status.REJECTED) {
      jdbc.update(
          """
              INSERT INTO change_approval (change_id, approver_id, decision, decided_at, comment)
              VALUES (?,?,?,?,?)
              """,
          id, actor, target.name(), java.sql.Timestamp.from(now), cabNotes
      );
    }

    Map<String, Object> before = Map.of("status", current.status().name());
    Map<String, Object> after = Map.of(
        "status", updated.status().name(),
        "cabNotes", String.valueOf(updated.cabNotes()),
        "cabRiskLevel", String.valueOf(updated.cabRiskLevel())
    );
    audit.append(new AuditTrail.Entry(
        actor, "change.transitioned", "change", id.toString(), before, after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "change.transitioned", 1, now, correlationId, "change", id.toString(), after
    ));
    Change saved = query.findById(id).orElseThrow();
    searchIndexer.index(saved);
    return saved;
  }

  @Transactional
  public Change update(UUID id, UpdateCommand command, String actor) {
    Change current = query.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Change not found: " + id));
    if (command.expectedVersion() < 0 || current.version() != command.expectedVersion()) {
      throw new OptimisticLockingFailureException(
          "Change changed since version " + command.expectedVersion());
    }
    if (current.status() == Change.Status.CLOSED || current.status() == Change.Status.REJECTED) {
      throw new IllegalStateException("Terminal change cannot be edited");
    }
    Instant plannedStart = command.plannedStart() == null ? current.plannedStart() : command.plannedStart();
    Instant plannedEnd = command.plannedEnd() == null ? current.plannedEnd() : command.plannedEnd();
    if (plannedStart != null && plannedEnd != null && !plannedEnd.isAfter(plannedStart)) {
      throw new IllegalArgumentException("plannedEnd must be after plannedStart");
    }
    String implementationPlan = command.implementationPlan() == null
        ? current.implementationPlan() : command.implementationPlan().trim();
    String rollbackPlan = command.rollbackPlan() == null
        ? current.rollbackPlan() : command.rollbackPlan().trim();
    if (implementationPlan == null || implementationPlan.isBlank()
        || rollbackPlan == null || rollbackPlan.isBlank()) {
      throw new IllegalArgumentException("implementationPlan and rollbackPlan are required");
    }
    String justification = command.businessJustification() == null
        ? current.businessJustification() : command.businessJustification().trim();
    String cabNotes = command.cabNotes() == null ? current.cabNotes() : command.cabNotes().trim();
    Change.Risk cabRisk = command.cabRiskLevel() == null ? current.cabRiskLevel() : command.cabRiskLevel();
    Instant now = Instant.now();
    int changed = jdbc.update(
        """
        UPDATE change_request SET planned_start=?, planned_end=?, implementation_plan=?, rollback_plan=?,
          test_plan=?, business_justification=?, cab_notes=?, cab_risk_level=?, impact=?, version=version+1, updated_at=?
        WHERE id=? AND org_id=? AND version=?
        """,
        plannedStart == null ? null : java.sql.Timestamp.from(plannedStart),
        plannedEnd == null ? null : java.sql.Timestamp.from(plannedEnd),
        implementationPlan, rollbackPlan,
        command.testPlan() == null ? current.testPlan() : command.testPlan(),
        justification, cabNotes,
        cabRisk == null ? null : cabRisk.name(),
        command.impact() == null ? current.impact() : command.impact(),
        java.sql.Timestamp.from(now),
        id, OrganizationContext.current(), command.expectedVersion());
    if (changed == 0) throw new OptimisticLockingFailureException(
        "Change changed since version " + command.expectedVersion());
    UUID correlation = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    Map<String, Object> before = Map.of("version", current.version(), "status", current.status().name());
    Map<String, Object> after = Map.of(
        "version", current.version() + 1,
        "status", current.status().name(),
        "cabRiskLevel", String.valueOf(cabRisk));
    audit.append(new AuditTrail.Entry(actor, "change.fields-updated", "change", id.toString(),
        before, after, correlation, now));
    outbox.record(new DomainEvent(UUID.randomUUID(), "change.fields-updated", 1, now, correlation,
        "change", id.toString(), after));
    Change saved = query.findById(id).orElseThrow();
    searchIndexer.index(saved);
    return saved;
  }

  public record CreateCommand(
      Change.Type type,
      Change.Risk risk,
      String title,
      Instant plannedStart,
      Instant plannedEnd,
      String implementationPlan,
      String rollbackPlan,
      String testPlan,
      String businessJustification,
      String cabNotes,
      Change.Risk cabRiskLevel,
      Change.Impact impact
  ) {}

  public record UpdateCommand(
      long expectedVersion,
      Instant plannedStart,
      Instant plannedEnd,
      String implementationPlan,
      String rollbackPlan,
      String testPlan,
      String businessJustification,
      String cabNotes,
      Change.Risk cabRiskLevel,
      Change.Impact impact
  ) {}
}
