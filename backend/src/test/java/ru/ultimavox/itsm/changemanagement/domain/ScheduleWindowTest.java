package ru.ultimavox.itsm.changemanagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScheduleWindowTest {

  private final Instant t0 = Instant.parse("2026-08-01T10:00:00Z");
  private final Instant t1 = Instant.parse("2026-08-01T12:00:00Z");
  private final Instant t2 = Instant.parse("2026-08-01T14:00:00Z");
  private final Instant t3 = Instant.parse("2026-08-01T16:00:00Z");

  @Test
  void overlaps_when_windows_intersect() {
    assertThat(ScheduleWindow.overlaps(t0, t2, t1, t3)).isTrue();
    assertThat(ScheduleWindow.overlaps(t1, t3, t0, t2)).isTrue();
  }

  @Test
  void no_overlap_when_adjacent() {
    // a ends exactly when b starts — not overlapping for half-open-ish rule
    assertThat(ScheduleWindow.overlaps(t0, t1, t1, t2)).isFalse();
  }

  @Test
  void no_overlap_when_disjoint() {
    assertThat(ScheduleWindow.overlaps(t0, t1, t2, t3)).isFalse();
  }

  @Test
  void rejects_null_or_inverted() {
    assertThat(ScheduleWindow.overlaps(null, t1, t0, t2)).isFalse();
    assertThat(ScheduleWindow.overlaps(t1, t0, t0, t2)).isFalse();
  }
}
