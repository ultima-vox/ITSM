package ru.ultimavox.itsm.platform.announcement;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;

/** Service announcements: one broadcast that every addressed operator sees at once. */
@Service
public class AnnouncementService {
  private static final String COLUMNS = """
      id, title, body, severity, audience, starts_at, ends_at, published, dismissible,
      link_url, created_by, created_at, updated_at, version
      """;

  private final JdbcTemplate jdbc;
  private final AuditTrail audit;

  public AnnouncementService(JdbcTemplate jdbc, AuditTrail audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  /** Every announcement, published or not — the administration view. */
  public List<Announcement> list() {
    return jdbc.query(
        "SELECT " + COLUMNS + " FROM announcement WHERE org_id = ? ORDER BY starts_at DESC",
        AnnouncementService::map,
        OrganizationContext.current());
  }

  /**
   * Published announcements whose window contains {@code at} and whose audience includes the
   * caller. An announcement with no end is open-ended until someone retires it.
   */
  public List<Announcement> active(Audience audience, Instant at) {
    Instant when = at == null ? Instant.now() : at;
    return jdbc.query(
        "SELECT " + COLUMNS + """
             FROM announcement
             WHERE org_id = ?
               AND published
               AND starts_at <= ?
               AND (ends_at IS NULL OR ends_at > ?)
               AND (audience = 'ALL' OR audience = ?)
             ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'WARNING' THEN 1 ELSE 2 END,
                      starts_at DESC
            """,
        AnnouncementService::map,
        OrganizationContext.current(),
        java.sql.Timestamp.from(when),
        java.sql.Timestamp.from(when),
        (audience == null ? Audience.ALL : audience).name());
  }

  public Optional<Announcement> findById(UUID id) {
    return jdbc.query(
        "SELECT " + COLUMNS + " FROM announcement WHERE id = ? AND org_id = ?",
        AnnouncementService::map,
        id, OrganizationContext.current()).stream().findFirst();
  }

  @Transactional
  public Announcement create(Command command, String actor) {
    validate(command);
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    jdbc.update(
        """
            INSERT INTO announcement (
              id, org_id, title, body, severity, audience, starts_at, ends_at, published,
              dismissible, link_url, created_by, created_at, updated_at, version
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)
            """,
        id, OrganizationContext.current(), command.title().trim(), command.body().trim(),
        command.severity().name(), command.audience().name(),
        java.sql.Timestamp.from(command.startsAt()),
        command.endsAt() == null ? null : java.sql.Timestamp.from(command.endsAt()),
        command.published(), command.dismissible(), command.linkUrl(), actor,
        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    record(actor, "announcement.created", id, Map.of(
        "title", command.title(), "severity", command.severity().name(),
        "published", command.published()));
    return findById(id).orElseThrow();
  }

  @Transactional
  public Announcement update(UUID id, long expectedVersion, Command command, String actor) {
    Announcement current = require(id);
    if (expectedVersion < 0 || current.version() != expectedVersion) {
      throw new OptimisticLockingFailureException("Announcement changed since version " + expectedVersion);
    }
    validate(command);
    int changed = jdbc.update(
        """
            UPDATE announcement
            SET title = ?, body = ?, severity = ?, audience = ?, starts_at = ?, ends_at = ?,
                published = ?, dismissible = ?, link_url = ?, version = version + 1, updated_at = ?
            WHERE id = ? AND org_id = ? AND version = ?
            """,
        command.title().trim(), command.body().trim(), command.severity().name(),
        command.audience().name(), java.sql.Timestamp.from(command.startsAt()),
        command.endsAt() == null ? null : java.sql.Timestamp.from(command.endsAt()),
        command.published(), command.dismissible(), command.linkUrl(),
        java.sql.Timestamp.from(Instant.now()), id, OrganizationContext.current(), expectedVersion);
    if (changed == 0) {
      throw new OptimisticLockingFailureException("Announcement changed since version " + expectedVersion);
    }
    record(actor, "announcement.updated", id, Map.of(
        "title", command.title(), "published", command.published()));
    return findById(id).orElseThrow();
  }

  /** Ends an announcement now rather than deleting it, so the audit keeps what was said. */
  @Transactional
  public Announcement retire(UUID id, String actor) {
    Announcement current = require(id);
    Instant now = Instant.now();
    jdbc.update(
        """
            UPDATE announcement SET ends_at = ?, version = version + 1, updated_at = ?
            WHERE id = ? AND org_id = ?
            """,
        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), id, OrganizationContext.current());
    record(actor, "announcement.retired", id, Map.of("title", current.title()));
    return findById(id).orElseThrow();
  }

  @Transactional
  public void delete(UUID id, String actor) {
    Announcement current = require(id);
    jdbc.update("DELETE FROM announcement WHERE id = ? AND org_id = ?", id, OrganizationContext.current());
    record(actor, "announcement.deleted", id, Map.of("title", current.title()));
  }

  private Announcement require(UUID id) {
    return findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Announcement not found: " + id));
  }

  private static void validate(Command command) {
    if (command.title() == null || command.title().isBlank()) {
      throw new IllegalArgumentException("title is required");
    }
    if (command.body() == null || command.body().isBlank()) {
      throw new IllegalArgumentException("body is required");
    }
    if (command.severity() == null || command.audience() == null) {
      throw new IllegalArgumentException("severity and audience are required");
    }
    if (command.startsAt() == null) {
      throw new IllegalArgumentException("startsAt is required");
    }
    if (command.endsAt() != null && !command.endsAt().isAfter(command.startsAt())) {
      throw new IllegalArgumentException("endsAt must be after startsAt");
    }
  }

  private void record(String actor, String action, UUID id, Map<String, Object> state) {
    audit.append(new AuditTrail.Entry(
        actor, action, "announcement", id.toString(), Map.of(), state,
        CorrelationContext.currentOrCreate(), Instant.now()));
  }

  private static Announcement map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    java.sql.Timestamp endsAt = rs.getTimestamp("ends_at");
    return new Announcement(
        rs.getObject("id", UUID.class),
        rs.getString("title"),
        rs.getString("body"),
        Severity.valueOf(rs.getString("severity")),
        Audience.valueOf(rs.getString("audience")),
        rs.getTimestamp("starts_at").toInstant(),
        endsAt == null ? null : endsAt.toInstant(),
        rs.getBoolean("published"),
        rs.getBoolean("dismissible"),
        rs.getString("link_url"),
        rs.getString("created_by"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        rs.getLong("version"));
  }

  public enum Severity { INFO, WARNING, CRITICAL }

  public enum Audience { ALL, AGENTS, REQUESTERS }

  public record Announcement(
      UUID id,
      String title,
      String body,
      Severity severity,
      Audience audience,
      Instant startsAt,
      Instant endsAt,
      boolean published,
      boolean dismissible,
      String linkUrl,
      String createdBy,
      Instant createdAt,
      Instant updatedAt,
      long version
  ) {}

  public record Command(
      String title,
      String body,
      Severity severity,
      Audience audience,
      Instant startsAt,
      Instant endsAt,
      boolean published,
      boolean dismissible,
      String linkUrl
  ) {}
}
