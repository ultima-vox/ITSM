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
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

@Service
public class UpdateWorkItem {

  private final WorkItemStore store;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final WorkItemSearchIndexer searchIndexer;

  UpdateWorkItem(
      WorkItemStore store,
      AuditTrail audit,
      IntegrationEventOutbox outbox,
      WorkItemSearchIndexer searchIndexer
  ) {
    this.store = store;
    this.audit = audit;
    this.outbox = outbox;
    this.searchIndexer = searchIndexer;
  }

  @Transactional
  public WorkItem update(UUID id, Command command, String actorId) {
    WorkItem existing = store.requireById(id);
    if (!existing.isOpen()) {
      throw new IllegalStateException("Cannot update a closed or cancelled work item");
    }

    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();

    String title = command.title() != null ? command.title().trim() : existing.title();
    String description = command.description() != null ? command.description().trim() : existing.description();
    String service = command.service() != null ? command.service().trim() : existing.service();
    Impact impact = command.impact() != null ? command.impact() : existing.impact();
    Urgency urgency = command.urgency() != null ? command.urgency() : existing.urgency();

    if (title.isBlank() || description.isBlank() || service.isBlank()) {
      throw new IllegalArgumentException("title, description and service must not be blank");
    }

    WorkItem updated = existing.withDetails(title, description, service, impact, urgency, now);
    store.update(updated);

    Map<String, Object> before = CreateWorkItem.snapshot(existing);
    Map<String, Object> after = CreateWorkItem.snapshot(updated);
    audit.append(new AuditTrail.Entry(
        actorId,
        "work-item.updated",
        "work-item",
        id.toString(),
        before,
        after,
        correlationId,
        now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(),
        "work-item.updated",
        1,
        now,
        correlationId,
        "work-item",
        id.toString(),
        after
    ));
    searchIndexer.index(updated);
    return updated;
  }

  public record Command(
      String title,
      String description,
      String service,
      Impact impact,
      Urgency urgency
  ) {}
}
