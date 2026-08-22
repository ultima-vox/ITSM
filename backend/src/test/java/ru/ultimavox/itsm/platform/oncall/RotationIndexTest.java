package ru.ultimavox.itsm.platform.oncall;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RotationIndexTest {
  private static final Instant START = Instant.parse("2026-08-03T09:00:00Z");

  private static JdbcOnCallDirectory.Schedule weekly() {
    return new JdbcOnCallDirectory.Schedule(UUID.randomUUID(), 168, START);
  }

  @Test
  void before_the_rotation_starts_the_first_participant_holds_it() {
    assertThat(JdbcOnCallDirectory.rotationIndex(weekly(), START.minus(5, ChronoUnit.DAYS), 3))
        .isZero();
    assertThat(JdbcOnCallDirectory.rotationIndex(weekly(), START, 3)).isZero();
  }

  @Test
  void the_rotation_advances_once_per_period_and_wraps() {
    var schedule = weekly();
    assertThat(JdbcOnCallDirectory.rotationIndex(schedule, START.plus(1, ChronoUnit.DAYS), 3)).isZero();
    assertThat(JdbcOnCallDirectory.rotationIndex(schedule, START.plus(7, ChronoUnit.DAYS), 3)).isEqualTo(1);
    assertThat(JdbcOnCallDirectory.rotationIndex(schedule, START.plus(14, ChronoUnit.DAYS), 3)).isEqualTo(2);
    assertThat(JdbcOnCallDirectory.rotationIndex(schedule, START.plus(21, ChronoUnit.DAYS), 3)).isZero();
    assertThat(JdbcOnCallDirectory.rotationIndex(schedule, START.plus(364, ChronoUnit.DAYS), 3)).isEqualTo(1);
  }

  @Test
  void a_daily_rotation_hands_over_every_day() {
    var daily = new JdbcOnCallDirectory.Schedule(UUID.randomUUID(), 24, START);
    assertThat(JdbcOnCallDirectory.rotationIndex(daily, START.plus(23, ChronoUnit.HOURS), 2)).isZero();
    assertThat(JdbcOnCallDirectory.rotationIndex(daily, START.plus(24, ChronoUnit.HOURS), 2)).isEqualTo(1);
    assertThat(JdbcOnCallDirectory.rotationIndex(daily, START.plus(48, ChronoUnit.HOURS), 2)).isZero();
  }

  @Test
  void a_single_participant_is_always_on_call() {
    var schedule = weekly();
    assertThat(JdbcOnCallDirectory.rotationIndex(schedule, START.plus(500, ChronoUnit.DAYS), 1)).isZero();
  }
}
