package ru.ultimavox.itsm.servicedesk.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

@Service
public class AssignWorkItem {

  private final WorkItemStore store;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  AssignWorkItem(WorkItemStore store, AuditTrail audit, IntegrationEventOutbox outbox) {
    this.store = store;
    this.audit = audit;
    this.outbox = outbox;
  }

  @Transactional
  public WorkItem assign(UUID id, Command command, String actorId) {
    if (command.assigneeId() == null || command.assigneeId().isBlank()) {
      throw new IllegalArgumentException("assigneeId is required");
    }

    WorkItem existing = store.requireById(id);
    if (!existing.isOpen()) {
      throw new IllegalStateException("Cannot assign a closed or cancelled work item");
    }

    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();
    String teamId = command.teamId() != null ? command.teamId() : existing.teamId();
    WorkItem updated = existing.assign(command.assigneeId().trim(), teamId, now);
    store.update(updated);

    Map<String, Object> before = CreateWorkItem.snapshot(existing);
    Map<String, Object> after = CreateWorkItem.snapshot(updated);
    audit.append(new AuditTrail.Entry(
        actorId,
        "work-item.assigned",
        "work-item",
        id.toString(),
        before,
        after,
        correlationId,
        now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(),
        "work-item.assigned",
        1,
        now,
        correlationId,
        "work-item",
        id.toString(),
        after
    ));
    return updated;
  }

  public record Command(String assigneeId, String teamId) {}
}
