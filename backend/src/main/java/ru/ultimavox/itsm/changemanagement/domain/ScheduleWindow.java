package ru.ultimavox.itsm.changemanagement.domain;

import java.time.Instant;

/** Pure helpers for change calendar overlap detection. */
public final class ScheduleWindow {
  private ScheduleWindow() {}

  /**
   * Half-open style: windows overlap when each starts before the other ends.
   * Returns false when any bound is null or end is not after start.
   */
  public static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
    if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
      return false;
    }
    if (!aEnd.isAfter(aStart) || !bEnd.isAfter(bStart)) {
      return false;
    }
    return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
  }
}
