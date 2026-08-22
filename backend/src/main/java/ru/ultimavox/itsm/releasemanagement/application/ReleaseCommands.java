package ru.ultimavox.itsm.releasemanagement.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.workflow.WorkflowPolicyGateway;
import ru.ultimavox.itsm.releasemanagement.domain.Release;

@Service
public class ReleaseCommands {
  private final JdbcTemplate jdbc;
  private final ReleaseQuery query;
  private final ReleaseContentService content;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final WorkflowPolicyGateway workflows;

  public ReleaseCommands(
      JdbcTemplate jdbc,
      ReleaseQuery query,
      ReleaseContentService content,
      AuditTrail audit,
      IntegrationEventOutbox outbox,
      WorkflowPolicyGateway workflows
  ) {
    this.jdbc = jdbc;
    this.query = query;
    this.content = content;
    this.audit = audit;
    this.outbox = outbox;
    this.workflows = workflows;
  }

  @Transactional
  public Release create(CreateCommand command, String actor) {
    if (command.name() == null || command.name().isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    if (command.plannedStart() != null && command.plannedEnd() != null
        && !command.plannedEnd().isAfter(command.plannedStart())) {
      throw new IllegalArgumentException("plannedEnd must be after plannedStart");
    }
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    UUID correlationId = CorrelationContext.currentOrCreate();
    Long sequence = jdbc.queryForObject("SELECT nextval('release_number_seq')", Long.class);
    String number = "REL-%06d".formatted(sequence);

    jdbc.update(
        """
            INSERT INTO release_record (
              id, org_id, number, name, type, status, description, deployment_plan, rollback_plan,
              release_manager, planned_start, planned_end, created_by, created_at, updated_at, version
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)
            """,
        id,
        OrganizationContext.current(),
        number,
        command.name().trim(),
        command.type().name(),
        Release.Status.PLANNING.name(),
        command.description(),
        command.deploymentPlan(),
        command.rollbackPlan(),
        command.releaseManager() == null ? actor : command.releaseManager(),
        timestamp(command.plannedStart()),
        timestamp(command.plannedEnd()),
        actor,
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now)
    );
    workflows.startIfDefined("release", id.toString());

    Map<String, Object> state = Map.of(
        "number", number,
        "name", command.name().trim(),
        "type", command.type().name(),
        "status", Release.Status.PLANNING.name()
    );
    audit.append(new AuditTrail.Entry(
        actor, "release.created", "release", id.toString(), Map.of(), state, correlationId, now));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "release.created", 1, now, correlationId, "release", id.toString(), state));
    return query.findById(id).orElseThrow();
  }

  @Transactional
  public Release update(UUID id, UpdateCommand command, String actor) {
    Release current = require(id);
    guardVersion(current, command.expectedVersion());
    if (current.contentFrozen()) {
      throw new IllegalStateException("A release that reached deployment cannot be edited");
    }
    Instant plannedStart = command.plannedStart() == null ? current.plannedStart() : command.plannedStart();
    Instant plannedEnd = command.plannedEnd() == null ? current.plannedEnd() : command.plannedEnd();
    if (plannedStart != null && plannedEnd != null && !plannedEnd.isAfter(plannedStart)) {
      throw new IllegalArgumentException("plannedEnd must be after plannedStart");
    }
    String name = command.name() == null ? current.name() : command.name().trim();
    if (name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    Instant now = Instant.now();
    int changed = jdbc.update(
        """
            UPDATE release_record
            SET name = ?, type = ?, description = ?, deployment_plan = ?, rollback_plan = ?,
                test_summary = ?, release_manager = ?, planned_start = ?, planned_end = ?,
                version = version + 1, updated_at = ?
            WHERE id = ? AND org_id = ? AND version = ?
            """,
        name,
        command.type() == null ? current.type().name() : command.type().name(),
        command.description() == null ? current.description() : command.description(),
        command.deploymentPlan() == null ? current.deploymentPlan() : command.deploymentPlan(),
        command.rollbackPlan() == null ? current.rollbackPlan() : command.rollbackPlan(),
        command.testSummary() == null ? current.testSummary() : command.testSummary(),
        command.releaseManager() == null ? current.releaseManager() : command.releaseManager(),
        timestamp(plannedStart),
        timestamp(plannedEnd),
        java.sql.Timestamp.from(now),
        id,
        OrganizationContext.current(),
        command.expectedVersion()
    );
    if (changed == 0) {
      throw new OptimisticLockingFailureException(
          "Release changed since version " + command.expectedVersion());
    }
    Map<String, Object> after = Map.of("name", name, "version", current.version() + 1);
    UUID correlationId = CorrelationContext.currentOrCreate();
    audit.append(new AuditTrail.Entry(actor, "release.updated", "release", id.toString(),
        Map.of("name", current.name(), "version", current.version()), after, correlationId, now));
    outbox.record(new DomainEvent(UUID.randomUUID(), "release.updated", 1, now, correlationId,
        "release", id.toString(), after));
    return query.findById(id).orElseThrow();
  }

  @Transactional
  public Release transition(UUID id, Release.Status target, Long expectedVersion, String actor) {
    Release current = require(id);
    long version = expectedVersion == null ? current.version() : expectedVersion;
    guardVersion(current, version);
    // The aggregate's own gates answer first: they are cheaper, and they name the missing
    // artefact precisely. Only a release that clears them is checked against its content.
    Release updated = current.transition(target);
    if (target == Release.Status.DEPLOYING) {
      List<ReleaseContentService.ContentEntry> blocking = content.notReadyForDeployment(id);
      if (!blocking.isEmpty()) {
        throw new IllegalStateException(
            "Every linked change must be approved before deployment: "
                + blocking.stream().map(ReleaseContentService.ContentEntry::number).toList());
      }
    }
    Instant now = Instant.now();
    UUID correlationId = CorrelationContext.currentOrCreate();
    workflows.enforceByTarget(
        actor, "release", id.toString(), current.status().name(), target.name(),
        new HashMap<>(), correlationId);

    int changed = jdbc.update(
        """
            UPDATE release_record
            SET status = ?, actual_start = ?, actual_end = ?, version = version + 1, updated_at = ?
            WHERE id = ? AND org_id = ? AND version = ?
            """,
        updated.status().name(),
        timestamp(updated.actualStart()),
        timestamp(updated.actualEnd()),
        java.sql.Timestamp.from(now),
        id,
        OrganizationContext.current(),
        version
    );
    if (changed == 0) {
      throw new OptimisticLockingFailureException("Release changed since version " + version);
    }
    Map<String, Object> after = Map.of("status", updated.status().name(), "number", updated.number());
    audit.append(new AuditTrail.Entry(actor, "release.transitioned", "release", id.toString(),
        Map.of("status", current.status().name()), after, correlationId, now));
    outbox.record(new DomainEvent(UUID.randomUUID(), "release." + updated.status().name().toLowerCase(),
        1, now, correlationId, "release", id.toString(), after));
    return query.findById(id).orElseThrow();
  }

  @Transactional
  public Release recordGoDecision(UUID id, Release.GoDecision decision, String notes,
                                  Long expectedVersion, String actor) {
    Release current = require(id);
    long version = expectedVersion == null ? current.version() : expectedVersion;
    guardVersion(current, version);
    Instant now = Instant.now();
    Release updated = current.withGoDecision(decision, notes, actor, now);
    int changed = jdbc.update(
        """
            UPDATE release_record
            SET go_decision = ?, go_decision_notes = ?, go_decided_by = ?, go_decided_at = ?,
                version = version + 1, updated_at = ?
            WHERE id = ? AND org_id = ? AND version = ?
            """,
        updated.goDecision().name(),
        notes,
        actor,
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now),
        id,
        OrganizationContext.current(),
        version
    );
    if (changed == 0) {
      throw new OptimisticLockingFailureException("Release changed since version " + version);
    }
    UUID correlationId = CorrelationContext.currentOrCreate();
    Map<String, Object> after = Map.of("goDecision", decision.name(), "number", current.number());
    audit.append(new AuditTrail.Entry(actor, "release.go-decision", "release", id.toString(),
        Map.of("goDecision", String.valueOf(current.goDecision())), after, correlationId, now));
    outbox.record(new DomainEvent(UUID.randomUUID(), "release.go-decision", 1, now, correlationId,
        "release", id.toString(), after));
    return query.findById(id).orElseThrow();
  }

  private Release require(UUID id) {
    return query.findById(id).orElseThrow(() -> new IllegalArgumentException("Release not found: " + id));
  }

  private static void guardVersion(Release current, long expectedVersion) {
    if (expectedVersion < 0 || current.version() != expectedVersion) {
      throw new OptimisticLockingFailureException("Release changed since version " + expectedVersion);
    }
  }

  private static java.sql.Timestamp timestamp(Instant instant) {
    return instant == null ? null : java.sql.Timestamp.from(instant);
  }

  public record CreateCommand(
      String name,
      Release.Type type,
      String description,
      String deploymentPlan,
      String rollbackPlan,
      String releaseManager,
      Instant plannedStart,
      Instant plannedEnd
  ) {}

  public record UpdateCommand(
      long expectedVersion,
      String name,
      Release.Type type,
      String description,
      String deploymentPlan,
      String rollbackPlan,
      String testSummary,
      String releaseManager,
      Instant plannedStart,
      Instant plannedEnd
  ) {}
}
