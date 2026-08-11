package ru.ultimavox.itsm.servicedesk.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
        store.averageCsatSince(Instant.now().minus(30, ChronoUnit.DAYS))
    );
  }

  public record Stats(long open, long dueToday, long breached, Double csat) {}
}
