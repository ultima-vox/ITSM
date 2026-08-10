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

@Service
public class WorkItemCiLinkService {

  private final WorkItemStore store;
  private final JdbcTemplate jdbc;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  WorkItemCiLinkService(
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

  public List<UUID> listCiIds(UUID workItemId) {
    store.requireById(workItemId);
    return jdbc.query(
        """
        SELECT configuration_item_id
        FROM work_item_configuration_item
        WHERE work_item_id = ?
        ORDER BY linked_at ASC
        """,
        (rs, i) -> rs.getObject("configuration_item_id", UUID.class),
        workItemId
    );
  }

  @Transactional
  public List<UUID> link(UUID workItemId, UUID ciId, String actorId) {
    store.requireById(workItemId);
    Integer exists = jdbc.queryForObject(
        "SELECT COUNT(*) FROM configuration_item WHERE id = ?",
        Integer.class,
        ciId
    );
    if (exists == null || exists == 0) {
      throw new IllegalArgumentException("Configuration item not found: " + ciId);
    }
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    try {
      jdbc.update(
          """
          INSERT INTO work_item_configuration_item (work_item_id, configuration_item_id, linked_by, linked_at)
          VALUES (?,?,?,?)
          """,
          workItemId,
          ciId,
          actorId,
          java.sql.Timestamp.from(now)
      );
    } catch (DuplicateKeyException ex) {
      return listCiIds(workItemId);
    }
    Map<String, Object> after = Map.of("configurationItemId", ciId.toString());
    audit.append(new AuditTrail.Entry(
        actorId, "work-item.ci-linked", "work-item", workItemId.toString(),
        Map.of(), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "work-item.ci-linked", 1, now, correlationId,
        "work-item", workItemId.toString(), after
    ));
    return listCiIds(workItemId);
  }

  @Transactional
  public List<UUID> unlink(UUID workItemId, UUID ciId, String actorId) {
    store.requireById(workItemId);
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    int n = jdbc.update(
        """
        DELETE FROM work_item_configuration_item
        WHERE work_item_id = ? AND configuration_item_id = ?
        """,
        workItemId,
        ciId
    );
    if (n > 0) {
      Map<String, Object> after = Map.of("configurationItemId", ciId.toString());
      audit.append(new AuditTrail.Entry(
          actorId, "work-item.ci-unlinked", "work-item", workItemId.toString(),
          Map.of(), after, correlationId, now
      ));
      outbox.record(new DomainEvent(
          UUID.randomUUID(), "work-item.ci-unlinked", 1, now, correlationId,
          "work-item", workItemId.toString(), after
      ));
    }
    return listCiIds(workItemId);
  }
}
