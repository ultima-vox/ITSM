package ru.ultimavox.itsm.platform.sla;

import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Tenant-scoped persistent calendar catalog with safe bootstrap fallback. */
@Component
public class WorkingCalendarRegistry {

    private final Map<String, WorkingCalendar> calendars = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;

    public WorkingCalendarRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        WorkingCalendar defaultBusiness = new WorkingCalendar(
                ZoneId.of("Europe/Moscow"),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                Set.of()
        );
        calendars.put("default-business", defaultBusiness);
        calendars.put("default", defaultBusiness);
    }

    public WorkingCalendar require(String calendarKey) {
        WorkingCalendar calendar = find(calendarKey);
        if (calendar == null) {
            throw new IllegalArgumentException("Unknown working calendar: " + calendarKey);
        }
        return calendar;
    }

    public WorkingCalendar find(String calendarKey) {
        List<WorkingCalendarView> rows = jdbc.query(
            """
            SELECT id, calendar_key, zone_id, working_days, starts_at, ends_at, holidays, version
            FROM working_calendar WHERE org_id=? AND calendar_key=?
            """,
            (rs, row) -> mapView(rs), OrganizationContext.current(), calendarKey);
        return rows.isEmpty() ? calendars.get(calendarKey) : rows.getFirst().calendar();
    }

    public List<WorkingCalendarView> list() {
        List<WorkingCalendarView> rows = jdbc.query(
            """
            SELECT DISTINCT ON (calendar_key)
              id, calendar_key, zone_id, working_days, starts_at, ends_at, holidays, version
            FROM working_calendar WHERE org_id IN (?, 'default')
            ORDER BY calendar_key, (org_id = ?) DESC
            """,
            (rs, row) -> mapView(rs), OrganizationContext.current(), OrganizationContext.current());
        if (!rows.isEmpty()) return rows;
        return List.of(new WorkingCalendarView(null, "default-business", calendars.get("default-business"), 0));
    }

    private static WorkingCalendarView mapView(java.sql.ResultSet rs) throws java.sql.SQLException {
        Set<DayOfWeek> days = java.util.Arrays.stream((Object[]) rs.getArray("working_days").getArray())
            .map(String::valueOf).map(DayOfWeek::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<LocalDate> holidays = java.util.Arrays.stream((Object[]) rs.getArray("holidays").getArray())
            .map(value -> ((java.sql.Date) value).toLocalDate())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        WorkingCalendar calendar = new WorkingCalendar(ZoneId.of(rs.getString("zone_id")), days,
            rs.getTime("starts_at").toLocalTime(), rs.getTime("ends_at").toLocalTime(), holidays);
        return new WorkingCalendarView(rs.getObject("id", java.util.UUID.class), rs.getString("calendar_key"),
            calendar, rs.getLong("version"));
    }

    public void register(String key, WorkingCalendar calendar) {
        calendars.put(key, calendar);
    }

    public record WorkingCalendarView(java.util.UUID id, String key, WorkingCalendar calendar, long version) {}
}
