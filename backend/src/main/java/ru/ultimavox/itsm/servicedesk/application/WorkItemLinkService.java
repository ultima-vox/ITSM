package ru.ultimavox.itsm.servicedesk.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItemLink;

@Service
public class WorkItemLinkService {

  private final WorkItemStore store;
  private final JdbcTemplate jdbc;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  WorkItemLinkService(
      WorkItemStore store,
      JdbcTemplate jdbc,
      AuditTrail audit,
      IntegrationEventOutbox outbox
  ) {
    this.store = store;
    this.jdbc = jdbc;
    this.audit = audit;
    this.outbox = outbox;
  }

  public List<WorkItemLink> listFor(UUID workItemId) {
    store.requireById(workItemId);
    return jdbc.query(
        """
        SELECT id, source_id, target_id, link_type, created_by, created_at
        FROM work_item_link
        WHERE source_id = ? OR target_id = ?
        ORDER BY created_at DESC
        """,
        (rs, i) -> new WorkItemLink(
            rs.getObject("id", UUID.class),
            rs.getObject("source_id", UUID.class),
            rs.getObject("target_id", UUID.class),
            WorkItemLink.Type.valueOf(rs.getString("link_type")),
            rs.getString("created_by"),
            rs.getTimestamp("created_at").toInstant()
        ),
        workItemId,
        workItemId
    );
  }

  @Transactional
  public WorkItemLink link(
      UUID sourceId,
      UUID targetId,
      WorkItemLink.Type type,
      String actorId
  ) {
    if (sourceId.equals(targetId)) {
      throw new IllegalArgumentException("Cannot link a work item to itself");
    }
    store.requireById(sourceId);
    store.requireById(targetId);
    Instant now = Instant.now();
    UUID id = UUID.randomUUID();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    try {
      jdbc.update(
          """
          INSERT INTO work_item_link (id, source_id, target_id, link_type, created_by, created_at)
          VALUES (?,?,?,?,?,?)
          """,
          id,
          sourceId,
          targetId,
          type.name(),
          actorId,
          java.sql.Timestamp.from(now)
      );
    } catch (DuplicateKeyException ex) {
      throw new IllegalStateException("Link already exists");
    }
    WorkItemLink link = new WorkItemLink(id, sourceId, targetId, type, actorId, now);
    Map<String, Object> after = Map.of(
        "linkId", id.toString(),
        "targetId", targetId.toString(),
        "linkType", type.name()
    );
    audit.append(new AuditTrail.Entry(
        actorId, "work-item.linked", "work-item", sourceId.toString(),
        Map.of(), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "work-item.linked", 1, now, correlationId,
        "work-item", sourceId.toString(), after
    ));
    return link;
  }

  @Transactional
  public void unlink(UUID sourceId, UUID linkId, String actorId) {
    store.requireById(sourceId);
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    int n = jdbc.update(
        "DELETE FROM work_item_link WHERE id = ? AND (source_id = ? OR target_id = ?)",
        linkId,
        sourceId,
        sourceId
    );
    if (n == 0) {
      throw new WorkItemNotFoundException(linkId);
    }
    Map<String, Object> after = Map.of("linkId", linkId.toString());
    audit.append(new AuditTrail.Entry(
        actorId, "work-item.unlinked", "work-item", sourceId.toString(),
        Map.of(), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "work-item.unlinked", 1, now, correlationId,
        "work-item", sourceId.toString(), after
    ));
  }
}
