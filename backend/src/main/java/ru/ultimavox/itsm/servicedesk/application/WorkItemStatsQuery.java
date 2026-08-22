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
    return stats(null, null);
  }

  public Stats stats(String actorId, String requesterScope) {
    return new Stats(
        store.countOpen(requesterScope),
        store.countMine(actorId, requesterScope),
        store.countUnassigned(requesterScope),
        store.countSlaDueToday(),
        store.countSlaBreached(requesterScope),
        store.averageCsatSince(Instant.now().minus(30, ChronoUnit.DAYS))
    );
  }

  public record Stats(
      long open,
      long mine,
      long unassigned,
      long dueToday,
      long breached,
      Double csat
  ) {}
}
