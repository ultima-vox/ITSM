package ru.ultimavox.itsm.servicedesk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

class WorkItemTest {

  private final Instant now = Instant.parse("2026-07-30T10:00:00Z");

  @ParameterizedTest
  @CsvSource({
      "HIGH,HIGH,CRITICAL",
      "HIGH,MEDIUM,HIGH",
      "HIGH,LOW,MEDIUM",
      "MEDIUM,HIGH,HIGH",
      "MEDIUM,MEDIUM,MEDIUM",
      "MEDIUM,LOW,LOW",
      "LOW,HIGH,MEDIUM",
      "LOW,MEDIUM,LOW",
      "LOW,LOW,LOW"
  })
  void priority_matrix_maps_impact_and_urgency(Impact impact, Urgency urgency, Priority expected) {
    assertThat(WorkItem.derivePriority(impact, urgency)).isEqualTo(expected);
  }

  @Test
  void happy_path_new_to_closed() {
    WorkItem item = sample(State.NEW);
    item = item.transition(State.IN_PROGRESS, null, null, now);
    assertThat(item.state()).isEqualTo(State.IN_PROGRESS);

    item = item.transition(State.PENDING, null, null, now);
    assertThat(item.state()).isEqualTo(State.PENDING);

    item = item.transition(State.IN_PROGRESS, null, null, now);
    item = item.transition(State.RESOLVED, "FIXED", "Restored VPN", now);
    assertThat(item.state()).isEqualTo(State.RESOLVED);
    assertThat(item.resolutionCode()).isEqualTo("FIXED");

    item = item.transition(State.CLOSED, "FIXED", "Confirmed by user", now);
    assertThat(item.state()).isEqualTo(State.CLOSED);
    assertThat(item.closedAt()).isEqualTo(now);
  }

  @Test
  void illegal_skip_from_new_to_resolved_is_rejected() {
    WorkItem item = sample(State.NEW);
    assertThatThrownBy(() -> item.transition(State.RESOLVED, "FIXED", "nope", now))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("NEW")
        .hasMessageContaining("RESOLVED");
  }

  @Test
  void cancel_from_new_is_allowed() {
    WorkItem cancelled = sample(State.NEW).transition(State.CANCELLED, "DUPLICATE", null, now);
    assertThat(cancelled.state()).isEqualTo(State.CANCELLED);
    assertThat(cancelled.closedAt()).isEqualTo(now);
  }

  @Test
  void resolve_requires_resolution_code() {
    WorkItem inProgress = sample(State.NEW).transition(State.IN_PROGRESS, null, null, now);
    assertThatThrownBy(() -> inProgress.transition(State.RESOLVED, null, "notes", now))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resolutionCode");
  }

  @Test
  void reopen_from_resolved_and_closed() {
    WorkItem resolved = sample(State.NEW)
        .transition(State.IN_PROGRESS, null, null, now)
        .transition(State.RESOLVED, "FIXED", "done", now);
    WorkItem reopened = resolved.transition(State.IN_PROGRESS, null, null, now);
    assertThat(reopened.state()).isEqualTo(State.IN_PROGRESS);
    assertThat(reopened.closedAt()).isNull();

    Instant closedAt = Instant.parse("2026-07-30T12:00:00Z");
    WorkItem closed = reopened.transition(State.RESOLVED, "FIXED", "again", now)
        .transition(State.CLOSED, "FIXED", "ok", closedAt);
    assertThat(closed.closedAt()).isEqualTo(closedAt);
    WorkItem reopenedClosed = closed.transition(State.IN_PROGRESS, null, null, now);
    assertThat(reopenedClosed.state()).isEqualTo(State.IN_PROGRESS);
    assertThat(reopenedClosed.closedAt()).isNull();
  }

  @Test
  void closed_is_terminal() {
    WorkItem closed = sample(State.NEW)
        .transition(State.IN_PROGRESS, null, null, now)
        .transition(State.RESOLVED, "FIXED", "done", now)
        .transition(State.CLOSED, "FIXED", "done", now);
    assertThatThrownBy(() -> closed.transition(State.IN_PROGRESS, null, null, now))
        .isInstanceOf(IllegalStateException.class);
  }

  private WorkItem sample(State state) {
    return new WorkItem(
        UUID.randomUUID(),
        "INC-001000",
        Type.INCIDENT,
        "VPN unavailable",
        "Remote employees cannot connect",
        "Workplace",
        state,
        Priority.MEDIUM,
        Impact.MEDIUM,
        Urgency.MEDIUM,
        null,
        "user-42",
        null,
        null,
        null,
        now,
        now,
        null
    );
  }
}
