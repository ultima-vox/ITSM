package ru.ultimavox.itsm.platform.sla;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/** Explicit business-time calendar. Multiple calendars can represent geography, support tier or contracts. */
public record WorkingCalendar(
        ZoneId zone,
        Set<DayOfWeek> workingDays,
        LocalTime startsAt,
        LocalTime endsAt,
        Set<LocalDate> holidays
) {
    public WorkingCalendar {
        Objects.requireNonNull(zone, "zone");
        workingDays = Set.copyOf(workingDays);
        holidays = Set.copyOf(holidays);
        if (workingDays.isEmpty() || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Calendar requires a non-empty same-day working window");
        }
    }

    public boolean isWorkingDate(LocalDate date) {
        return workingDays.contains(date.getDayOfWeek()) && !holidays.contains(date);
    }
}
