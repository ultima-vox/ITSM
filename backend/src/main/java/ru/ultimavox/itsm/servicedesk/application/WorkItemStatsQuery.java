package ru.ultimavox.itsm.servicedesk.application;

import org.springframework.stereotype.Service;

@Service
public class WorkItemStatsQuery {

  private final WorkItemStore store;

  WorkItemStatsQuery(WorkItemStore store) {
    this.store = store;
  }

  public Stats stats() {
    return new Stats(
        store.countOpen(),
        store.countSlaDueToday(),
        store.countSlaBreached(),
        null
    );
  }

  /** csat is a placeholder until survey integration exists. */
  public record Stats(long open, long dueToday, long breached, Double csat) {}
}
