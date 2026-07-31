package ru.ultimavox.itsm.platform.sla;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/** Calculates elapsed business time; callers persist every recalculation as auditable SLA clock history. */
@Component
public class SlaDeadlineCalculator {

    public Instant deadline(Instant startedAt, Duration target, WorkingCalendar calendar) {
        if (target.isNegative()) {
            throw new IllegalArgumentException("Target cannot be negative");
        }
        if (target.isZero()) {
            return startedAt;
        }

        Instant cursor = startedAt;
        Duration remaining = target;

        while (!remaining.isZero()) {
            ZonedDateTime local = cursor.atZone(calendar.zone());
            LocalDate day = local.toLocalDate();

            if (!calendar.isWorkingDate(day) || !local.toLocalTime().isBefore(calendar.endsAt())) {
                cursor = nextStart(day.plusDays(1), calendar);
                continue;
            }
            if (local.toLocalTime().isBefore(calendar.startsAt())) {
                cursor = day.atTime(calendar.startsAt()).atZone(calendar.zone()).toInstant();
                continue;
            }

            Instant end = day.atTime(calendar.endsAt()).atZone(calendar.zone()).toInstant();
            Duration available = Duration.between(cursor, end);
            if (!remaining.minus(available).isPositive()) {
                return cursor.plus(remaining);
            }
            remaining = remaining.minus(available);
            cursor = nextStart(day.plusDays(1), calendar);
        }
        return cursor;
    }

    private Instant nextStart(LocalDate date, WorkingCalendar calendar) {
        LocalDate candidate = date;
        for (int guard = 0; guard < 370; guard++, candidate = candidate.plusDays(1)) {
            if (calendar.isWorkingDate(candidate)) {
                return candidate.atTime(calendar.startsAt()).atZone(calendar.zone()).toInstant();
            }
        }
        throw new IllegalStateException("No working day found in calendar year");
    }
}
