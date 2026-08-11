package ru.ultimavox.itsm.platform.sla;

import java.sql.Date;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.sla.WorkingCalendarRegistry.WorkingCalendarView;

@Service
public class WorkingCalendarAdminService {
  private final JdbcTemplate jdbc;
  private final WorkingCalendarRegistry registry;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public WorkingCalendarAdminService(JdbcTemplate jdbc, WorkingCalendarRegistry registry,
      AuditTrail audit, IntegrationEventOutbox outbox) {
    this.jdbc = jdbc;
    this.registry = registry;
    this.audit = audit;
    this.outbox = outbox;
  }

  public List<WorkingCalendarView> list() { return registry.list(); }

  @Transactional
  public WorkingCalendarView create(String actor, Command command) {
    validate(command);
    UUID id = UUID.randomUUID();
    try {
      jdbc.update("""
          INSERT INTO working_calendar
            (id,org_id,calendar_key,zone_id,working_days,starts_at,ends_at,holidays)
          VALUES (?,?,?, ?,?::varchar[],?,?,?::date[])
          """, id, OrganizationContext.current(), command.key().trim(), command.zone().trim(),
          dayNames(command.workingDays()), Time.valueOf(command.startsAt()), Time.valueOf(command.endsAt()),
          dates(command.holidays()));
    } catch (DuplicateKeyException ex) {
      throw new IllegalStateException("Working calendar already exists: " + command.key());
    }
    append(actor, "sla.calendar-created", command.key(), 0);
    return registry.list().stream().filter(v -> command.key().equals(v.key())).findFirst().orElseThrow();
  }

  @Transactional
  public WorkingCalendarView update(String actor, UUID id, long expectedVersion, Command command) {
    validate(command);
    int changed = jdbc.update("""
        UPDATE working_calendar SET calendar_key=?,zone_id=?,working_days=?::varchar[],starts_at=?,ends_at=?,
          holidays=?::date[],version=version+1,updated_at=now()
        WHERE id=? AND org_id=? AND version=?
        """, command.key().trim(), command.zone().trim(), dayNames(command.workingDays()),
        Time.valueOf(command.startsAt()), Time.valueOf(command.endsAt()), dates(command.holidays()),
        id, OrganizationContext.current(), expectedVersion);
    if (changed == 0) throw new OptimisticLockingFailureException(
        "Working calendar changed since version " + expectedVersion);
    append(actor, "sla.calendar-updated", command.key(), expectedVersion + 1);
    return registry.list().stream().filter(v -> id.equals(v.id())).findFirst().orElseThrow();
  }

  private static void validate(Command command) {
    if (command == null) throw new IllegalArgumentException("calendar is required");
    if (command.key() == null || command.key().isBlank()) throw new IllegalArgumentException("key is required");
    if (command.zone() == null || command.zone().isBlank()) throw new IllegalArgumentException("zone is required");
    if (command.startsAt() == null || command.endsAt() == null)
      throw new IllegalArgumentException("working window is required");
    ZoneId.of(command.zone());
    if (command.workingDays() == null || command.workingDays().isEmpty())
      throw new IllegalArgumentException("workingDays are required");
    new WorkingCalendar(ZoneId.of(command.zone()), Set.copyOf(command.workingDays()),
        command.startsAt(), command.endsAt(), Set.copyOf(command.holidays() == null ? Set.of() : command.holidays()));
  }

  private void append(String actor, String action, String key, long version) {
    Instant now = Instant.now();
    UUID correlation = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    Map<String, Object> value = Map.of("key", key, "version", version);
    audit.append(new AuditTrail.Entry(actor, action, "working-calendar", key, Map.of(), value, correlation, now));
    outbox.record(new DomainEvent(UUID.randomUUID(), action, 1, now, correlation,
        "working-calendar", key, value));
  }

  private static String[] dayNames(Set<DayOfWeek> values) {
    return values.stream().sorted().map(Enum::name).toArray(String[]::new);
  }
  private static Date[] dates(Set<LocalDate> values) {
    if (values == null) return new Date[0];
    return values.stream().sorted().map(Date::valueOf).toArray(Date[]::new);
  }

  public record Command(String key, String zone, Set<DayOfWeek> workingDays,
                        LocalTime startsAt, LocalTime endsAt, Set<LocalDate> holidays) {}
}
