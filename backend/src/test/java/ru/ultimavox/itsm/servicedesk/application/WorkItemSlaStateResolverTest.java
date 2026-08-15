package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.sla.SlaClock;
import ru.ultimavox.itsm.platform.sla.SlaClockRepository;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

class WorkItemSlaStateResolverTest {

  private final SlaClockRepository clocks = mock(SlaClockRepository.class);
  private final WorkItemSlaStateResolver resolver = new WorkItemSlaStateResolver(clocks);
  private final UUID id = UUID.randomUUID();
  private final Instant now = Instant.now();

  @Test
  void terminal_item_reports_met_without_querying_clocks() {
    assertThat(resolver.forWorkItem(item(State.CLOSED))).isEqualTo(
        new WorkItemSlaStateResolver.SlaSnapshot("met", null, null));
    verifyNoInteractions(clocks);
  }

  @Test
  void no_clock_yields_no_snapshot() {
    when(clocks.findActive(id, "response")).thenReturn(Optional.empty());
    assertThat(resolver.forWorkItem(item(State.NEW))).isNull();
  }

  @Test
  void past_due_clock_reports_breached() {
    SlaClock clock = clock(now.minusSeconds(60), now.minusSeconds(120), SlaClock.State.RUNNING);
    when(clocks.findActive(id, "response")).thenReturn(Optional.of(clock));
    assertThat(resolver.forWorkItem(item(State.IN_PROGRESS)).state()).isEqualTo("breached");
  }

  @Test
  void warning_window_reports_at_risk() {
    SlaClock clock = clock(now.plusSeconds(300), now.minusSeconds(60), SlaClock.State.RUNNING);
    when(clocks.findActive(id, "response")).thenReturn(Optional.of(clock));
    assertThat(resolver.forWorkItem(item(State.IN_PROGRESS)).state()).isEqualTo("at_risk");
  }

  @Test
  void healthy_clock_reports_on_track_and_exposes_deadlines() {
    SlaClock clock = clock(now.plusSeconds(600), now.plusSeconds(300), SlaClock.State.RUNNING);
    when(clocks.findActive(id, "response")).thenReturn(Optional.of(clock));
    WorkItemSlaStateResolver.SlaSnapshot snapshot = resolver.forWorkItem(item(State.IN_PROGRESS));
    assertThat(snapshot.state()).isEqualTo("on_track");
    assertThat(snapshot.dueAt()).isEqualTo(now.plusSeconds(600));
    assertThat(snapshot.warningAt()).isEqualTo(now.plusSeconds(300));
  }

  private WorkItem item(State state) {
    return new WorkItem(
        id, "INC-001842", Type.INCIDENT, "VPN unavailable", "Cannot connect",
        "Workplace", state, Priority.CRITICAL, Impact.HIGH, Urgency.HIGH,
        "agent-9", "user-42", "sd-l1", null, null, false, now, now, null);
  }

  private SlaClock clock(Instant dueAt, Instant warningAt, SlaClock.State state) {
    return new SlaClock(
        UUID.randomUUID(), "work-item.response.default", id, "response",
        now, dueAt, warningAt, null, state);
  }
}
