package ru.ultimavox.itsm.platform.oncall;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Service
class JdbcOnCallDirectory implements OnCallDirectory {
  private final JdbcTemplate jdbc;

  JdbcOnCallDirectory(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<String> onCall(String scheduleKey, Instant at) {
    if (scheduleKey == null || scheduleKey.isBlank() || at == null) {
      return Optional.empty();
    }
    String org = OrganizationContext.current();
    List<Schedule> schedules = jdbc.query(
        """
            SELECT id, rotation_hours, rotation_start
            FROM on_call_schedule
            WHERE org_id = ? AND schedule_key = ? AND active
            """,
        (rs, row) -> new Schedule(
            rs.getObject("id", UUID.class),
            rs.getInt("rotation_hours"),
            rs.getTimestamp("rotation_start").toInstant()),
        org, scheduleKey.trim());
    if (schedules.isEmpty()) {
      return Optional.empty();
    }
    Schedule schedule = schedules.getFirst();

    List<String> override = jdbc.queryForList(
        """
            SELECT subject FROM on_call_override
            WHERE org_id = ? AND schedule_id = ? AND starts_at <= ? AND ends_at > ?
            ORDER BY starts_at DESC
            LIMIT 1
            """,
        String.class,
        org, schedule.id(), java.sql.Timestamp.from(at), java.sql.Timestamp.from(at));
    if (!override.isEmpty()) {
      return Optional.of(override.getFirst());
    }

    List<String> participants = jdbc.queryForList(
        "SELECT subject FROM on_call_participant WHERE schedule_id = ? ORDER BY position",
        String.class,
        schedule.id());
    if (participants.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(participants.get(rotationIndex(schedule, at, participants.size())));
  }

  /**
   * Index of the participant holding the rotation at {@code at}. Before the rotation starts the
   * first participant holds it; after it, the index advances every {@code rotation_hours} and
   * wraps, so the maths stays correct however far in the future the question is asked.
   */
  static int rotationIndex(Schedule schedule, Instant at, int participantCount) {
    if (!at.isAfter(schedule.rotationStart())) {
      return 0;
    }
    long elapsedHours = Duration.between(schedule.rotationStart(), at).toHours();
    long periods = elapsedHours / schedule.rotationHours();
    return (int) Math.floorMod(periods, participantCount);
  }

  @Override
  public List<Responder> escalationChain(String policyKey, Instant at) {
    if (policyKey == null || policyKey.isBlank() || at == null) {
      return List.of();
    }
    String org = OrganizationContext.current();
    List<UUID> policies = jdbc.queryForList(
        "SELECT id FROM escalation_policy WHERE org_id = ? AND policy_key = ? AND active",
        UUID.class,
        org, policyKey.trim());
    if (policies.isEmpty()) {
      return List.of();
    }
    List<Step> steps = jdbc.query(
        """
            SELECT step_order, delay_minutes, target_type, target_ref
            FROM escalation_step WHERE policy_id = ? ORDER BY step_order
            """,
        (rs, row) -> new Step(
            rs.getInt("step_order"),
            rs.getInt("delay_minutes"),
            rs.getString("target_type"),
            rs.getString("target_ref")),
        policies.getFirst());

    List<Responder> chain = new ArrayList<>();
    for (Step step : steps) {
      if ("SUBJECT".equals(step.targetType())) {
        chain.add(new Responder(step.stepOrder(), step.delayMinutes(), step.targetRef(), "SUBJECT"));
        continue;
      }
      onCall(step.targetRef(), at).ifPresent(subject ->
          chain.add(new Responder(step.stepOrder(), step.delayMinutes(), subject, step.targetRef())));
    }
    return List.copyOf(chain);
  }

  record Schedule(UUID id, int rotationHours, Instant rotationStart) {}

  private record Step(int stepOrder, int delayMinutes, String targetType, String targetRef) {}
}
