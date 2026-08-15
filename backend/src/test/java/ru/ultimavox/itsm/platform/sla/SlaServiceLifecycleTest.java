package ru.ultimavox.itsm.platform.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

/** Lifecycle behaviour of {@link SlaService}: pause/resume for pauseable business states. */
@ExtendWith(MockitoExtension.class)
class SlaServiceLifecycleTest {

  @Mock SlaPolicyRepository policies;
  @Mock SlaClockRepository clocks;
  @Mock WorkingCalendarRegistry calendars;
  @Mock IntegrationEventOutbox outbox;

  private final UUID aggregateId = UUID.randomUUID();
  private final Instant now = Instant.now();

  private final SlaPolicy responsePolicy = new SlaPolicy(
      UUID.randomUUID(),
      "work-item.response.default",
      "standard",
      List.of(new SlaPolicy.Target("response", "", Duration.ofHours(4), Duration.ofHours(2))),
      Set.of("PENDING")
  );

  private final WorkingCalendar calendar = new WorkingCalendar(
      ZoneId.of("UTC"),
      Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
      LocalTime.of(9, 0),
      LocalTime.of(18, 0),
      Set.of()
  );

  private SlaService service() {
    return new SlaService(
        policies, clocks, new SlaDeadlineCalculator(), calendars, outbox, new ObjectMapper());
  }

  private SlaClock clock(Instant dueAt, Instant warningAt, Instant pausedAt, SlaClock.State state) {
    return new SlaClock(
        UUID.randomUUID(),
        "work-item.response.default",
        aggregateId,
        "response",
        now.minusSeconds(7200),
        dueAt,
        warningAt,
        pausedAt,
        state
    );
  }

  @Test
  void pause_for_state_pauses_running_clocks_listed_in_policy() {
    SlaClock running = clock(now.plusSeconds(7200), now.plusSeconds(3600), null, SlaClock.State.RUNNING);
    when(clocks.findActiveByAggregate(aggregateId)).thenReturn(List.of(running));
    when(clocks.findById(running.id())).thenReturn(Optional.of(running));
    when(policies.findByKey("work-item.response.default")).thenReturn(Optional.of(responsePolicy));

    service().pauseForState(aggregateId, "PENDING", "agent-1");

    ArgumentCaptor<SlaClock> captor = ArgumentCaptor.forClass(SlaClock.class);
    verify(clocks).update(captor.capture());
    assertThat(captor.getValue().state()).isEqualTo(SlaClock.State.PAUSED);
    assertThat(captor.getValue().pausedAt()).isNotNull();
    verify(clocks).appendHistory(eq(running.id()), eq("PAUSE"), eq("agent-1"), any());
  }

  @Test
  void pause_for_state_skips_clocks_not_listed_in_policy() {
    SlaClock running = clock(now.plusSeconds(7200), null, null, SlaClock.State.RUNNING);
    SlaPolicy policy = new SlaPolicy(
        UUID.randomUUID(), "work-item.response.default", "standard",
        List.of(new SlaPolicy.Target("response", "", Duration.ofHours(4), null)), Set.of());
    when(clocks.findActiveByAggregate(aggregateId)).thenReturn(List.of(running));
    when(policies.findByKey("work-item.response.default")).thenReturn(Optional.of(policy));

    service().pauseForState(aggregateId, "PENDING", "agent-1");

    verify(clocks, never()).update(any());
  }

  @Test
  void is_pauseable_true_when_active_clock_policy_lists_state() {
    SlaClock running = clock(now.plusSeconds(7200), null, null, SlaClock.State.RUNNING);
    when(clocks.findActiveByAggregate(aggregateId)).thenReturn(List.of(running));
    when(policies.findByKey("work-item.response.default")).thenReturn(Optional.of(responsePolicy));

    assertThat(service().isPauseable(aggregateId, "PENDING")).isTrue();
    assertThat(service().isPauseable(aggregateId, "IN_PROGRESS")).isFalse();
  }

  @Test
  void is_pauseable_false_without_active_clocks() {
    when(clocks.findActiveByAggregate(aggregateId)).thenReturn(List.of());

    assertThat(service().isPauseable(aggregateId, "PENDING")).isFalse();
  }

  @Test
  void is_pauseable_false_when_policy_missing() {
    SlaClock running = clock(now.plusSeconds(7200), null, null, SlaClock.State.RUNNING);
    when(clocks.findActiveByAggregate(aggregateId)).thenReturn(List.of(running));
    when(policies.findByKey("work-item.response.default")).thenReturn(Optional.empty());

    assertThat(service().isPauseable(aggregateId, "PENDING")).isFalse();
  }

  @Test
  void resume_all_resumes_paused_clock_and_skips_running() {
    SlaClock paused = clock(now.plusSeconds(7200), null, now.minusSeconds(3600), SlaClock.State.PAUSED);
    SlaClock running = clock(now.plusSeconds(7200), null, null, SlaClock.State.RUNNING);
    when(clocks.findActiveByAggregate(aggregateId)).thenReturn(List.of(paused, running));
    when(clocks.findById(paused.id())).thenReturn(Optional.of(paused));
    when(policies.findByKey("work-item.response.default")).thenReturn(Optional.of(responsePolicy));
    when(calendars.require("standard")).thenReturn(calendar);

    List<SlaClock> resumed = service().resumeAll(aggregateId, "agent-1");

    assertThat(resumed).hasSize(1);
    ArgumentCaptor<SlaClock> captor = ArgumentCaptor.forClass(SlaClock.class);
    verify(clocks).update(captor.capture());
    assertThat(captor.getValue().id()).isEqualTo(paused.id());
    assertThat(captor.getValue().state()).isEqualTo(SlaClock.State.RUNNING);
    assertThat(captor.getValue().pausedAt()).isNull();
    assertThat(captor.getValue().dueAt()).isAfter(now.minusSeconds(3600));
    verify(clocks).appendHistory(eq(paused.id()), eq("RESUME"), eq("agent-1"), any());
  }

  @Test
  void resume_all_is_noop_without_paused_clocks() {
    SlaClock running = clock(now.plusSeconds(7200), null, null, SlaClock.State.RUNNING);
    when(clocks.findActiveByAggregate(aggregateId)).thenReturn(List.of(running));

    List<SlaClock> resumed = service().resumeAll(aggregateId, "agent-1");

    assertThat(resumed).isEmpty();
    verify(clocks, never()).update(any());
  }
}
