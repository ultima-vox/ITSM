package ru.ultimavox.itsm.platform.oncall;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;

/** Administration of on-call rotations, overrides and escalation policies. */
@Service
public class OnCallAdminService {
  private static final int MAX_PARTICIPANTS = 50;
  private static final int MAX_STEPS = 20;

  private final JdbcTemplate jdbc;
  private final AuditTrail audit;

  public OnCallAdminService(JdbcTemplate jdbc, AuditTrail audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  public List<Schedule> listSchedules() {
    String org = OrganizationContext.current();
    return jdbc.query(
        """
            SELECT id, schedule_key, name, time_zone, rotation_hours, rotation_start, active
            FROM on_call_schedule WHERE org_id = ? ORDER BY schedule_key
            """,
        (rs, row) -> new Schedule(
            rs.getObject("id", UUID.class),
            rs.getString("schedule_key"),
            rs.getString("name"),
            rs.getString("time_zone"),
            rs.getInt("rotation_hours"),
            rs.getTimestamp("rotation_start").toInstant(),
            rs.getBoolean("active"),
            participants(rs.getObject("id", UUID.class))),
        org);
  }

  public Schedule getSchedule(String scheduleKey) {
    return listSchedules().stream()
        .filter(schedule -> schedule.scheduleKey().equals(scheduleKey))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleKey));
  }

  @Transactional
  public Schedule createSchedule(ScheduleCommand command, String actor) {
    validateSchedule(command);
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    try {
      jdbc.update(
          """
              INSERT INTO on_call_schedule (
                id, org_id, schedule_key, name, time_zone, rotation_hours, rotation_start,
                active, created_at, updated_at
              ) VALUES (?,?,?,?,?,?,?,?,?,?)
              """,
          id, OrganizationContext.current(), command.scheduleKey().trim(), command.name().trim(),
          command.timeZone(), command.rotationHours(),
          java.sql.Timestamp.from(command.rotationStart()), command.active(),
          java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    } catch (DuplicateKeyException ex) {
      throw new IllegalStateException("Schedule already exists: " + command.scheduleKey());
    }
    replaceParticipants(id, command.participants());
    record(actor, "oncall.schedule-created", id, Map.of(
        "scheduleKey", command.scheduleKey(), "participants", command.participants().size()));
    return getSchedule(command.scheduleKey().trim());
  }

  @Transactional
  public Schedule updateSchedule(String scheduleKey, ScheduleCommand command, String actor) {
    Schedule current = getSchedule(scheduleKey);
    validateSchedule(command);
    jdbc.update(
        """
            UPDATE on_call_schedule
            SET name = ?, time_zone = ?, rotation_hours = ?, rotation_start = ?, active = ?, updated_at = ?
            WHERE id = ? AND org_id = ?
            """,
        command.name().trim(), command.timeZone(), command.rotationHours(),
        java.sql.Timestamp.from(command.rotationStart()), command.active(),
        java.sql.Timestamp.from(Instant.now()), current.id(), OrganizationContext.current());
    replaceParticipants(current.id(), command.participants());
    record(actor, "oncall.schedule-updated", current.id(), Map.of("scheduleKey", scheduleKey));
    return getSchedule(scheduleKey);
  }

  @Transactional
  public void deleteSchedule(String scheduleKey, String actor) {
    Schedule current = getSchedule(scheduleKey);
    jdbc.update("DELETE FROM on_call_schedule WHERE id = ? AND org_id = ?",
        current.id(), OrganizationContext.current());
    record(actor, "oncall.schedule-deleted", current.id(), Map.of("scheduleKey", scheduleKey));
  }

  public List<Override> listOverrides(String scheduleKey) {
    Schedule schedule = getSchedule(scheduleKey);
    return jdbc.query(
        """
            SELECT id, subject, starts_at, ends_at, reason
            FROM on_call_override WHERE org_id = ? AND schedule_id = ?
            ORDER BY starts_at DESC
            """,
        (rs, row) -> new Override(
            rs.getObject("id", UUID.class),
            rs.getString("subject"),
            rs.getTimestamp("starts_at").toInstant(),
            rs.getTimestamp("ends_at").toInstant(),
            rs.getString("reason")),
        OrganizationContext.current(), schedule.id());
  }

  @Transactional
  public Override addOverride(String scheduleKey, OverrideCommand command, String actor) {
    Schedule schedule = getSchedule(scheduleKey);
    if (command.subject() == null || command.subject().isBlank()) {
      throw new IllegalArgumentException("subject is required");
    }
    if (command.startsAt() == null || command.endsAt() == null
        || !command.endsAt().isAfter(command.startsAt())) {
      throw new IllegalArgumentException("endsAt must be after startsAt");
    }
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
            INSERT INTO on_call_override (
              id, org_id, schedule_id, subject, starts_at, ends_at, reason, created_by, created_at
            ) VALUES (?,?,?,?,?,?,?,?,?)
            """,
        id, OrganizationContext.current(), schedule.id(), command.subject().trim(),
        java.sql.Timestamp.from(command.startsAt()), java.sql.Timestamp.from(command.endsAt()),
        command.reason(), actor, java.sql.Timestamp.from(Instant.now()));
    record(actor, "oncall.override-added", id, Map.of(
        "scheduleKey", scheduleKey, "subject", command.subject()));
    return listOverrides(scheduleKey).stream()
        .filter(override -> override.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public void deleteOverride(String scheduleKey, UUID overrideId, String actor) {
    Schedule schedule = getSchedule(scheduleKey);
    int removed = jdbc.update(
        "DELETE FROM on_call_override WHERE id = ? AND schedule_id = ? AND org_id = ?",
        overrideId, schedule.id(), OrganizationContext.current());
    if (removed == 0) {
      throw new IllegalArgumentException("Override not found: " + overrideId);
    }
    record(actor, "oncall.override-deleted", overrideId, Map.of("scheduleKey", scheduleKey));
  }

  public List<Policy> listPolicies() {
    return jdbc.query(
        """
            SELECT id, policy_key, name, active FROM escalation_policy
            WHERE org_id = ? ORDER BY policy_key
            """,
        (rs, row) -> new Policy(
            rs.getObject("id", UUID.class),
            rs.getString("policy_key"),
            rs.getString("name"),
            rs.getBoolean("active"),
            steps(rs.getObject("id", UUID.class))),
        OrganizationContext.current());
  }

  public Policy getPolicy(String policyKey) {
    return listPolicies().stream()
        .filter(policy -> policy.policyKey().equals(policyKey))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Escalation policy not found: " + policyKey));
  }

  @Transactional
  public Policy createPolicy(PolicyCommand command, String actor) {
    validatePolicy(command);
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    try {
      jdbc.update(
          """
              INSERT INTO escalation_policy (id, org_id, policy_key, name, active, created_at, updated_at)
              VALUES (?,?,?,?,?,?,?)
              """,
          id, OrganizationContext.current(), command.policyKey().trim(), command.name().trim(),
          command.active(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    } catch (DuplicateKeyException ex) {
      throw new IllegalStateException("Escalation policy already exists: " + command.policyKey());
    }
    replaceSteps(id, command.steps());
    record(actor, "oncall.policy-created", id, Map.of(
        "policyKey", command.policyKey(), "steps", command.steps().size()));
    return getPolicy(command.policyKey().trim());
  }

  @Transactional
  public Policy updatePolicy(String policyKey, PolicyCommand command, String actor) {
    Policy current = getPolicy(policyKey);
    validatePolicy(command);
    jdbc.update(
        "UPDATE escalation_policy SET name = ?, active = ?, updated_at = ? WHERE id = ? AND org_id = ?",
        command.name().trim(), command.active(), java.sql.Timestamp.from(Instant.now()),
        current.id(), OrganizationContext.current());
    replaceSteps(current.id(), command.steps());
    record(actor, "oncall.policy-updated", current.id(), Map.of("policyKey", policyKey));
    return getPolicy(policyKey);
  }

  @Transactional
  public void deletePolicy(String policyKey, String actor) {
    Policy current = getPolicy(policyKey);
    jdbc.update("DELETE FROM escalation_policy WHERE id = ? AND org_id = ?",
        current.id(), OrganizationContext.current());
    record(actor, "oncall.policy-deleted", current.id(), Map.of("policyKey", policyKey));
  }

  private void replaceParticipants(UUID scheduleId, List<String> participants) {
    jdbc.update("DELETE FROM on_call_participant WHERE schedule_id = ?", scheduleId);
    int position = 0;
    for (String subject : participants) {
      jdbc.update(
          "INSERT INTO on_call_participant (schedule_id, position, subject) VALUES (?,?,?)",
          scheduleId, position++, subject.trim());
    }
  }

  private void replaceSteps(UUID policyId, List<StepCommand> steps) {
    jdbc.update("DELETE FROM escalation_step WHERE policy_id = ?", policyId);
    int order = 0;
    for (StepCommand step : steps) {
      jdbc.update(
          """
              INSERT INTO escalation_step (policy_id, step_order, delay_minutes, target_type, target_ref)
              VALUES (?,?,?,?,?)
              """,
          policyId, order++, step.delayMinutes(), step.targetType(), step.targetRef().trim());
    }
  }

  private List<String> participants(UUID scheduleId) {
    return jdbc.queryForList(
        "SELECT subject FROM on_call_participant WHERE schedule_id = ? ORDER BY position",
        String.class, scheduleId);
  }

  private List<Step> steps(UUID policyId) {
    return jdbc.query(
        """
            SELECT step_order, delay_minutes, target_type, target_ref
            FROM escalation_step WHERE policy_id = ? ORDER BY step_order
            """,
        (rs, row) -> new Step(
            rs.getInt("step_order"),
            rs.getInt("delay_minutes"),
            rs.getString("target_type"),
            rs.getString("target_ref")),
        policyId);
  }

  private static void validateSchedule(ScheduleCommand command) {
    if (command.scheduleKey() == null || command.scheduleKey().isBlank()) {
      throw new IllegalArgumentException("scheduleKey is required");
    }
    if (command.name() == null || command.name().isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    if (command.rotationHours() < 1 || command.rotationHours() > 8760) {
      throw new IllegalArgumentException("rotationHours must be between 1 and 8760");
    }
    if (command.rotationStart() == null) {
      throw new IllegalArgumentException("rotationStart is required");
    }
    if (command.participants() == null || command.participants().isEmpty()) {
      throw new IllegalArgumentException("a schedule needs at least one participant");
    }
    if (command.participants().size() > MAX_PARTICIPANTS) {
      throw new IllegalArgumentException("at most " + MAX_PARTICIPANTS + " participants");
    }
    if (command.participants().stream().anyMatch(subject -> subject == null || subject.isBlank())) {
      throw new IllegalArgumentException("a participant cannot be blank");
    }
    if (command.timeZone() == null || command.timeZone().isBlank()) {
      throw new IllegalArgumentException("timeZone is required");
    }
    try {
      java.time.ZoneId.of(command.timeZone());
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Unknown time zone: " + command.timeZone());
    }
  }

  private static void validatePolicy(PolicyCommand command) {
    if (command.policyKey() == null || command.policyKey().isBlank()) {
      throw new IllegalArgumentException("policyKey is required");
    }
    if (command.name() == null || command.name().isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    if (command.steps() == null || command.steps().isEmpty()) {
      throw new IllegalArgumentException("an escalation policy needs at least one step");
    }
    if (command.steps().size() > MAX_STEPS) {
      throw new IllegalArgumentException("at most " + MAX_STEPS + " steps");
    }
    int previousDelay = -1;
    for (StepCommand step : command.steps()) {
      if (!"SUBJECT".equals(step.targetType()) && !"SCHEDULE".equals(step.targetType())) {
        throw new IllegalArgumentException("targetType must be SUBJECT or SCHEDULE");
      }
      if (step.targetRef() == null || step.targetRef().isBlank()) {
        throw new IllegalArgumentException("targetRef is required");
      }
      if (step.delayMinutes() < 0 || step.delayMinutes() > 10080) {
        throw new IllegalArgumentException("delayMinutes must be between 0 and 10080");
      }
      if (step.delayMinutes() < previousDelay) {
        throw new IllegalArgumentException("steps must be ordered by a non-decreasing delay");
      }
      previousDelay = step.delayMinutes();
    }
  }

  private void record(String actor, String action, UUID objectId, Map<String, Object> state) {
    audit.append(new AuditTrail.Entry(
        actor, action, "oncall", objectId.toString(), Map.of(), state,
        CorrelationContext.currentOrCreate(), Instant.now()));
  }

  public record Schedule(
      UUID id,
      String scheduleKey,
      String name,
      String timeZone,
      int rotationHours,
      Instant rotationStart,
      boolean active,
      List<String> participants
  ) {}

  public record Override(UUID id, String subject, Instant startsAt, Instant endsAt, String reason) {}

  public record Policy(UUID id, String policyKey, String name, boolean active, List<Step> steps) {}

  public record Step(int stepOrder, int delayMinutes, String targetType, String targetRef) {}

  public record ScheduleCommand(
      String scheduleKey,
      String name,
      String timeZone,
      int rotationHours,
      Instant rotationStart,
      boolean active,
      List<String> participants
  ) {}

  public record OverrideCommand(String subject, Instant startsAt, Instant endsAt, String reason) {}

  public record PolicyCommand(String policyKey, String name, boolean active, List<StepCommand> steps) {}

  public record StepCommand(int delayMinutes, String targetType, String targetRef) {}
}
