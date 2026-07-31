package ru.ultimavox.itsm.platform.sla;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory calendar catalog. Replace with DB-backed calendars when multi-tenant calendars land. */
@Component
public class WorkingCalendarRegistry {

    private final Map<String, WorkingCalendar> calendars = new ConcurrentHashMap<>();

    public WorkingCalendarRegistry() {
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
        WorkingCalendar calendar = calendars.get(calendarKey);
        if (calendar == null) {
            throw new IllegalArgumentException("Unknown working calendar: " + calendarKey);
        }
        return calendar;
    }

    public void register(String key, WorkingCalendar calendar) {
        calendars.put(key, calendar);
    }
}
