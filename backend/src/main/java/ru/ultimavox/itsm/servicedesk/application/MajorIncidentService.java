package ru.ultimavox.itsm.servicedesk.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

@Service
public class MajorIncidentService {

  private final WorkItemStore store;
  private final JdbcTemplate jdbc;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  MajorIncidentService(
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

  @Transactional
  public View declare(UUID workItemId, String commander, String summary, String actor) {
    WorkItem item = store.requireById(workItemId);
    if (item.type() != WorkItem.Type.INCIDENT) {
      throw new IllegalStateException("Only incidents may be declared major");
    }
    if (!item.isOpen()) {
      throw new IllegalStateException("Closed incident cannot be declared major");
    }
    if (commander == null || commander.isBlank()) {
      throw new IllegalArgumentException("commander is required");
    }
    if (summary == null || summary.isBlank()) {
      throw new IllegalArgumentException("summary is required");
    }
    if (find(workItemId).isPresent()) {
      throw new IllegalStateException("Major incident already declared");
    }

    Instant now = Instant.now();
    View view = new View(
        UUID.randomUUID(), workItemId, "DECLARED", commander.trim(), summary.trim(), now, null
    );
    jdbc.update(
        """
        INSERT INTO major_incident(
          id, org_id, work_item_id, status, commander_id, summary, declared_at
        ) VALUES(?,?,?,?,?,?,?)
        """,
        view.id(), OrganizationContext.current(), workItemId, view.status(), view.commanderId(),
        view.summary(), Timestamp.from(now)
    );
    record(actor, "major-incident.declared", view, now);
    return view;
  }

  @Transactional
  public View resolve(UUID workItemId, String actor) {
    View current = require(workItemId);
    if (!"DECLARED".equals(current.status())) {
      throw new IllegalStateException("Major incident already resolved");
    }
    Instant now = Instant.now();
    int updated = jdbc.update(
        """
        UPDATE major_incident SET status='RESOLVED', resolved_at=?
        WHERE work_item_id=? AND org_id=? AND status='DECLARED'
        """,
        Timestamp.from(now), workItemId, OrganizationContext.current()
    );
    if (updated != 1) {
      throw new IllegalStateException("Major incident changed concurrently");
    }
    View view = new View(
        current.id(), workItemId, "RESOLVED", current.commanderId(), current.summary(),
        current.declaredAt(), now
    );
    record(actor, "major-incident.resolved", view, now);
    return view;
  }

  public Optional<View> find(UUID workItemId) {
    return jdbc.query(
        """
        SELECT id, work_item_id, status, commander_id, summary, declared_at, resolved_at
        FROM major_incident WHERE work_item_id=? AND org_id=?
        """,
        (rs, rowNum) -> new View(
            rs.getObject("id", UUID.class),
            rs.getObject("work_item_id", UUID.class),
            rs.getString("status"),
            rs.getString("commander_id"),
            rs.getString("summary"),
            rs.getTimestamp("declared_at").toInstant(),
            rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toInstant()
        ),
        workItemId,
        OrganizationContext.current()
    ).stream().findFirst();
  }

  private View require(UUID workItemId) {
    return find(workItemId)
        .orElseThrow(() -> new IllegalArgumentException("Major incident not found"));
  }

  private void record(String actor, String action, View view, Instant now) {
    UUID correlation = CorrelationContext.currentOrCreate();
    Map<String, Object> data = Map.of(
        "status", view.status(), "commanderId", view.commanderId()
    );
    audit.append(new AuditTrail.Entry(
        actor, action, "major-incident", view.id().toString(), Map.of(), data, correlation, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), action, 1, now, correlation,
        "major-incident", view.id().toString(), data
    ));
  }

  public record View(
      UUID id,
      UUID workItemId,
      String status,
      String commanderId,
      String summary,
      Instant declaredAt,
      Instant resolvedAt
  ) {}
}
