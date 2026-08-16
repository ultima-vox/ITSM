package ru.ultimavox.itsm.servicedesk.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Service
public class WorkItemWatcherService {

  private final WorkItemStore store;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  WorkItemWatcherService(WorkItemStore store, AuditTrail audit, IntegrationEventOutbox outbox) {
    this.store = store;
    this.audit = audit;
    this.outbox = outbox;
  }

  public List<String> list(UUID workItemId) {
    store.requireById(workItemId);
    return store.listWatchers(workItemId);
  }

  @Transactional
  public List<String> watch(UUID workItemId, String subjectId) {
    if (subjectId == null || subjectId.isBlank()) {
      throw new IllegalArgumentException("subjectId is required");
    }
    store.requireById(workItemId);
    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();
    boolean already = store.isWatching(workItemId, subjectId);
    store.addWatcher(workItemId, subjectId, now);
    if (!already) {
      Map<String, Object> after = Map.of("watcherSubject", subjectId);
      audit.append(new AuditTrail.Entry(
          subjectId,
          "work-item.watcher-added",
          "work-item",
          workItemId.toString(),
          Map.of(),
          after,
          correlationId,
          now
      ));
      outbox.record(new DomainEvent(
          UUID.randomUUID(),
          "work-item.watcher-added",
          1,
          now,
          correlationId,
          "work-item",
          workItemId.toString(),
          after
      ));
    }
    return store.listWatchers(workItemId);
  }

  @Transactional
  public List<String> unwatch(UUID workItemId, String subjectId) {
    if (subjectId == null || subjectId.isBlank()) {
      throw new IllegalArgumentException("subjectId is required");
    }
    store.requireById(workItemId);
    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();
    boolean removed = store.removeWatcher(workItemId, subjectId);
    if (removed) {
      Map<String, Object> after = Map.of("watcherSubject", subjectId);
      audit.append(new AuditTrail.Entry(
          subjectId,
          "work-item.watcher-removed",
          "work-item",
          workItemId.toString(),
          Map.of(),
          after,
          correlationId,
          now
      ));
      outbox.record(new DomainEvent(
          UUID.randomUUID(),
          "work-item.watcher-removed",
          1,
          now,
          correlationId,
          "work-item",
          workItemId.toString(),
          after
      ));
    }
    return store.listWatchers(workItemId);
  }
}
