package ru.ultimavox.itsm.servicedesk.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Service
public class QueueSavedViewService {
  static final int MAX_VIEWS_PER_OWNER = 50;
  private static final Set<String> TABS = Set.of("unassigned", "mygroup", "escalated", "breached", "all");
  private static final Set<String> PRIORITIES = Set.of("", "critical", "high", "medium", "low");
  private static final Set<String> TYPES = Set.of("", "incident", "request", "change", "problem");
  private static final Set<String> STATUSES = Set.of(
      "", "new", "in_progress", "waiting", "resolved", "closed", "cancelled");
  private static final Set<String> SLAS = Set.of("", "breached", "at_risk", "on_track", "met");

  private final JdbcTemplate jdbc;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public QueueSavedViewService(JdbcTemplate jdbc, AuditTrail audit, IntegrationEventOutbox outbox) {
    this.jdbc = jdbc;
    this.audit = audit;
    this.outbox = outbox;
  }

  @Transactional(readOnly = true)
  public List<SavedView> list(String ownerSubject) {
    requireSubject(ownerSubject);
    return jdbc.query(
        """
        SELECT id, name, tab, priority, type, status, sla, created_at, updated_at
        FROM queue_saved_view
        WHERE org_id = ? AND owner_subject = ?
        ORDER BY created_at ASC
        """,
        (rs, i) -> new SavedView(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("tab"),
            rs.getString("priority"),
            rs.getString("type"),
            rs.getString("status"),
            rs.getString("sla"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()),
        OrganizationContext.current(),
        ownerSubject);
  }

  @Transactional
  public SavedView create(String ownerSubject, Command command) {
    requireSubject(ownerSubject);
    Command normalized = normalize(command);
    Long count = jdbc.queryForObject(
        "SELECT count(*) FROM queue_saved_view WHERE org_id = ? AND owner_subject = ?",
        Long.class,
        OrganizationContext.current(),
        ownerSubject);
    if (count != null && count >= MAX_VIEWS_PER_OWNER) {
      throw new IllegalStateException("Saved view limit reached");
    }
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    try {
      jdbc.update(
          """
          INSERT INTO queue_saved_view (
            id, org_id, owner_subject, name, tab, priority, type, status, sla, created_at, updated_at
          ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
          """,
          id,
          OrganizationContext.current(),
          ownerSubject,
          normalized.name(),
          normalized.tab(),
          normalized.priority(),
          normalized.type(),
          normalized.status(),
          normalized.sla(),
          Timestamp.from(now),
          Timestamp.from(now));
    } catch (DuplicateKeyException ex) {
      throw new DuplicateNameException(normalized.name());
    }
    SavedView created = new SavedView(
        id, normalized.name(), normalized.tab(), normalized.priority(), normalized.type(),
        normalized.status(), normalized.sla(), now, now);
    UUID correlation = CorrelationContext.currentOrCreate();
    Map<String, Object> after = asMap(created);
    audit.append(new AuditTrail.Entry(
        ownerSubject, "queue-view.created", "queue_saved_view", id.toString(), Map.of(), after, correlation, now));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "queue-view.created", 1, now, correlation, "queue_saved_view", id.toString(), after));
    return created;
  }

  @Transactional
  public void delete(String ownerSubject, UUID id) {
    requireSubject(ownerSubject);
    if (id == null) {
      throw new IllegalArgumentException("id is required");
    }
    Instant now = Instant.now();
    int removed = jdbc.update(
        "DELETE FROM queue_saved_view WHERE id = ? AND org_id = ? AND owner_subject = ?",
        id, OrganizationContext.current(), ownerSubject);
    if (removed == 0) {
      throw new NotFoundException(id);
    }
    UUID correlation = CorrelationContext.currentOrCreate();
    Map<String, Object> after = Map.of("id", id.toString());
    audit.append(new AuditTrail.Entry(
        ownerSubject, "queue-view.deleted", "queue_saved_view", id.toString(), Map.of(), after, correlation, now));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "queue-view.deleted", 1, now, correlation, "queue_saved_view", id.toString(), after));
  }

  private static Command normalize(Command command) {
    if (command == null || command.name() == null || command.name().isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    String name = command.name().trim();
    if (name.length() > 80) {
      throw new IllegalArgumentException("name is too long");
    }
    String tab = blankToEmpty(command.tab());
    if (tab.isEmpty()) {
      tab = "all";
    }
    String priority = blankToEmpty(command.priority());
    String type = blankToEmpty(command.type());
    String status = blankToEmpty(command.status());
    String sla = blankToEmpty(command.sla());
    if (!TABS.contains(tab)) {
      throw new IllegalArgumentException("tab is invalid");
    }
    if (!PRIORITIES.contains(priority)) {
      throw new IllegalArgumentException("priority is invalid");
    }
    if (!TYPES.contains(type)) {
      throw new IllegalArgumentException("type is invalid");
    }
    if (!STATUSES.contains(status)) {
      throw new IllegalArgumentException("status is invalid");
    }
    if (!SLAS.contains(sla)) {
      throw new IllegalArgumentException("sla is invalid");
    }
    return new Command(name, tab, priority, type, status, sla);
  }

  private static String blankToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private static void requireSubject(String ownerSubject) {
    if (ownerSubject == null || ownerSubject.isBlank()) {
      throw new IllegalArgumentException("owner is required");
    }
  }

  private static Map<String, Object> asMap(SavedView view) {
    return Map.of(
        "name", view.name(),
        "tab", view.tab(),
        "priority", view.priority(),
        "type", view.type(),
        "status", view.status(),
        "sla", view.sla());
  }

  public record Command(String name, String tab, String priority, String type, String status, String sla) {}

  public record SavedView(
      UUID id,
      String name,
      String tab,
      String priority,
      String type,
      String status,
      String sla,
      Instant createdAt,
      Instant updatedAt
  ) {}

  public static final class DuplicateNameException extends RuntimeException {
    DuplicateNameException(String name) {
      super("A saved view named '" + name + "' already exists");
    }
  }

  public static final class NotFoundException extends RuntimeException {
    NotFoundException(UUID id) {
      super("Saved view not found: " + id);
    }
  }
}
