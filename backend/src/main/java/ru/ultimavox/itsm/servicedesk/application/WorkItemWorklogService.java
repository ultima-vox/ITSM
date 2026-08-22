package ru.ultimavox.itsm.servicedesk.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

/**
 * Effort actually spent on a work item. The SLA clock measures elapsed time; a worklog
 * measures the agent's hands-on minutes, which is what effort reporting and billing need.
 */
@Service
public class WorkItemWorklogService {
  private static final int MAX_MINUTES_PER_ENTRY = 1440;
  /** Small tolerance so a clock skew between the browser and the server is not a validation error. */
  private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

  private final JdbcTemplate jdbc;
  private final WorkItemStore store;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public WorkItemWorklogService(
      JdbcTemplate jdbc,
      WorkItemStore store,
      AuditTrail audit,
      IntegrationEventOutbox outbox
  ) {
    this.jdbc = jdbc;
    this.store = store;
    this.audit = audit;
    this.outbox = outbox;
  }

  public Summary list(UUID workItemId) {
    store.requireById(workItemId);
    List<Entry> entries = jdbc.query(
        """
            SELECT id, work_item_id, author_subject, minutes, started_at, note, billable, created_at, updated_at
            FROM work_item_worklog
            WHERE org_id = ? AND work_item_id = ?
            ORDER BY started_at DESC, created_at DESC
            """,
        (rs, row) -> new Entry(
            rs.getObject("id", UUID.class),
            rs.getObject("work_item_id", UUID.class),
            rs.getString("author_subject"),
            rs.getInt("minutes"),
            rs.getTimestamp("started_at").toInstant(),
            rs.getString("note"),
            rs.getBoolean("billable"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        ),
        OrganizationContext.current(), workItemId);
    int total = entries.stream().mapToInt(Entry::minutes).sum();
    int billable = entries.stream().filter(Entry::billable).mapToInt(Entry::minutes).sum();
    return new Summary(entries, total, billable);
  }

  @Transactional
  public Entry log(UUID workItemId, LogCommand command, String actor) {
    store.requireById(workItemId);
    validate(command.minutes(), command.startedAt());
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    jdbc.update(
        """
            INSERT INTO work_item_worklog (
              id, org_id, work_item_id, author_subject, minutes, started_at, note, billable,
              created_at, updated_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?)
            """,
        id,
        OrganizationContext.current(),
        workItemId,
        actor,
        command.minutes(),
        java.sql.Timestamp.from(command.startedAt()),
        trim(command.note()),
        command.billable(),
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now));

    record(actor, "work-item.time-logged", workItemId, id, Map.of(
        "worklogId", id.toString(),
        "minutes", command.minutes(),
        "billable", command.billable()), now);
    return find(id).orElseThrow();
  }

  @Transactional
  public Entry update(UUID workItemId, UUID worklogId, UpdateCommand command,
                      String actor, boolean canManageAnyAuthor) {
    Entry current = requireEntry(workItemId, worklogId);
    requireAuthorOrManager(current, actor, canManageAnyAuthor);
    int minutes = command.minutes() == null ? current.minutes() : command.minutes();
    Instant startedAt = command.startedAt() == null ? current.startedAt() : command.startedAt();
    validate(minutes, startedAt);
    Instant now = Instant.now();
    jdbc.update(
        """
            UPDATE work_item_worklog
            SET minutes = ?, started_at = ?, note = ?, billable = ?, updated_at = ?
            WHERE id = ? AND org_id = ? AND work_item_id = ?
            """,
        minutes,
        java.sql.Timestamp.from(startedAt),
        command.note() == null ? current.note() : trim(command.note()),
        command.billable() == null ? current.billable() : command.billable(),
        java.sql.Timestamp.from(now),
        worklogId,
        OrganizationContext.current(),
        workItemId);

    record(actor, "work-item.time-updated", workItemId, worklogId, Map.of(
        "worklogId", worklogId.toString(),
        "minutes", minutes), now);
    return find(worklogId).orElseThrow();
  }

  @Transactional
  public void delete(UUID workItemId, UUID worklogId, String actor, boolean canManageAnyAuthor) {
    Entry current = requireEntry(workItemId, worklogId);
    requireAuthorOrManager(current, actor, canManageAnyAuthor);
    jdbc.update(
        "DELETE FROM work_item_worklog WHERE id = ? AND org_id = ? AND work_item_id = ?",
        worklogId, OrganizationContext.current(), workItemId);
    record(actor, "work-item.time-deleted", workItemId, worklogId, Map.of(
        "worklogId", worklogId.toString(),
        "minutes", current.minutes()), Instant.now());
  }

  private void record(String actor, String action, UUID workItemId, UUID worklogId,
                      Map<String, Object> state, Instant at) {
    UUID correlationId = CorrelationContext.currentOrCreate();
    audit.append(new AuditTrail.Entry(
        actor, action, "work-item", workItemId.toString(), Map.of(), state, correlationId, at));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), action, 1, at, correlationId, "work-item", workItemId.toString(), state));
  }

  private Entry requireEntry(UUID workItemId, UUID worklogId) {
    store.requireById(workItemId);
    Entry entry = find(worklogId)
        .orElseThrow(() -> new IllegalArgumentException("Worklog not found: " + worklogId));
    if (!entry.workItemId().equals(workItemId)) {
      throw new IllegalArgumentException("Worklog not found: " + worklogId);
    }
    return entry;
  }

  private static void requireAuthorOrManager(Entry entry, String actor, boolean canManageAnyAuthor) {
    if (!canManageAnyAuthor && !entry.authorSubject().equals(actor)) {
      throw new IllegalStateException("Only the author can change this worklog");
    }
  }

  private static void validate(int minutes, Instant startedAt) {
    if (minutes <= 0 || minutes > MAX_MINUTES_PER_ENTRY) {
      throw new IllegalArgumentException(
          "minutes must be between 1 and " + MAX_MINUTES_PER_ENTRY);
    }
    if (startedAt == null) {
      throw new IllegalArgumentException("startedAt is required");
    }
    if (startedAt.isAfter(Instant.now().plus(FUTURE_TOLERANCE))) {
      throw new IllegalArgumentException("startedAt cannot be in the future");
    }
  }

  private static String trim(String note) {
    if (note == null) return null;
    String trimmed = note.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private java.util.Optional<Entry> find(UUID worklogId) {
    return jdbc.query(
        """
            SELECT id, work_item_id, author_subject, minutes, started_at, note, billable, created_at, updated_at
            FROM work_item_worklog WHERE id = ? AND org_id = ?
            """,
        (rs, row) -> new Entry(
            rs.getObject("id", UUID.class),
            rs.getObject("work_item_id", UUID.class),
            rs.getString("author_subject"),
            rs.getInt("minutes"),
            rs.getTimestamp("started_at").toInstant(),
            rs.getString("note"),
            rs.getBoolean("billable"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        ),
        worklogId, OrganizationContext.current()).stream().findFirst();
  }

  public record Entry(
      UUID id,
      UUID workItemId,
      String authorSubject,
      int minutes,
      Instant startedAt,
      String note,
      boolean billable,
      Instant createdAt,
      Instant updatedAt
  ) {}

  public record Summary(List<Entry> items, int totalMinutes, int billableMinutes) {}

  public record LogCommand(int minutes, Instant startedAt, String note, boolean billable) {}

  public record UpdateCommand(Integer minutes, Instant startedAt, String note, Boolean billable) {}
}
