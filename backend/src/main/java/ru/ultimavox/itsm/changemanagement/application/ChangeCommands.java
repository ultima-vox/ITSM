package ru.ultimavox.itsm.changemanagement.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.changemanagement.domain.Change;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Service
public class ChangeCommands {
  private final JdbcTemplate jdbc;
  private final ChangeQuery query;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public ChangeCommands(JdbcTemplate jdbc, ChangeQuery query, AuditTrail audit, IntegrationEventOutbox outbox) {
    this.jdbc = jdbc;
    this.query = query;
    this.audit = audit;
    this.outbox = outbox;
  }

  @Transactional
  public Change create(CreateCommand command, String actor) {
    UUID id = UUID.randomUUID();
    UUID correlationId = UUID.randomUUID();
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
              id, number, type, risk, status, title, planned_start, planned_end,
              implementation_plan, rollback_plan, requester_id, created_at, updated_at,
              business_justification, cab_notes, cab_risk_level
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
        id, number, change.type().name(), change.risk().name(), change.status().name(), change.title(),
        change.plannedStart() == null ? null : java.sql.Timestamp.from(change.plannedStart()),
        change.plannedEnd() == null ? null : java.sql.Timestamp.from(change.plannedEnd()),
        change.implementationPlan(), change.rollbackPlan(), actor, now, now,
        change.businessJustification(), change.cabNotes(),
        change.cabRiskLevel() == null ? null : change.cabRiskLevel().name()
    );

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
    return change;
  }

  @Transactional
  public Change transition(UUID id, Change.Status target, String cabNotes, Change.Risk cabRiskLevel, String actor) {
    Change current = query.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Change not found: " + id));

    Change withCab = cabNotes != null || cabRiskLevel != null
        ? current.withCabAssessment(
            cabNotes != null ? cabNotes : current.cabNotes(),
            cabRiskLevel != null ? cabRiskLevel : current.cabRiskLevel()
        )
        : current;
    Change updated = withCab.transition(target);
    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();

    jdbc.update(
        """
            UPDATE change_request
            SET status = ?, cab_notes = ?, cab_risk_level = ?, updated_at = ?
            WHERE id = ?
            """,
        updated.status().name(),
        updated.cabNotes(),
        updated.cabRiskLevel() == null ? null : updated.cabRiskLevel().name(),
        now,
        id
    );

    if (target == Change.Status.APPROVED || target == Change.Status.REJECTED) {
      jdbc.update(
          """
              INSERT INTO change_approval (change_id, approver_id, decision, decided_at, comment)
              VALUES (?,?,?,?,?)
              """,
          id, actor, target.name(), now, cabNotes
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
    return updated;
  }

  public record CreateCommand(
      Change.Type type,
      Change.Risk risk,
      String title,
      Instant plannedStart,
      Instant plannedEnd,
      String implementationPlan,
      String rollbackPlan,
      String businessJustification,
      String cabNotes,
      Change.Risk cabRiskLevel
  ) {}
}
