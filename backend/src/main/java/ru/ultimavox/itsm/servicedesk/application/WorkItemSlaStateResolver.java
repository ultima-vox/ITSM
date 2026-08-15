package ru.ultimavox.itsm.servicedesk.application;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.sla.SlaClock;
import ru.ultimavox.itsm.platform.sla.SlaClockRepository;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

/**
 * Derives the SLA status of a work item from its live {@code response} clock so the API and UI
 * reflect real clock state instead of client-side guesses.
 *
 * <p>Values match the frontend contract: {@code on_track | at_risk | breached | met}. Terminal
 * items report {@code met}; items without any clock report no snapshot and callers fall back to
 * derived state.
 */
@Service
public class WorkItemSlaStateResolver {

  static final String RESPONSE_METRIC = "response";

  private final SlaClockRepository clocks;

  public WorkItemSlaStateResolver(SlaClockRepository clocks) {
    this.clocks = clocks;
  }

  /** Null when the item has no active clock and is not terminal. */
  public SlaSnapshot forWorkItem(WorkItem item) {
    if (item.isTerminal()) {
      return new SlaSnapshot("met", null, null);
    }
    Optional<SlaClock> clock = clocks.findActive(item.id(), RESPONSE_METRIC);
    if (clock.isEmpty()) {
      return null;
    }
    SlaClock active = clock.get();
    Instant now = Instant.now();
    String state;
    if (active.state() == SlaClock.State.PAUSED) {
      state = "on_track";
    } else if (active.state() == SlaClock.State.BREACHED || !active.dueAt().isAfter(now)) {
      state = "breached";
    } else if (active.warningAt() != null && !active.warningAt().isAfter(now)) {
      state = "at_risk";
    } else {
      state = "on_track";
    }
    return new SlaSnapshot(state, active.dueAt(), active.warningAt());
  }

  public record SlaSnapshot(String state, Instant dueAt, Instant warningAt) {}
}
