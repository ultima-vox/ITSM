package ru.ultimavox.itsm.platform.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Repository
public class JdbcNotificationStore implements NotificationStore {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  JdbcNotificationStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public StoredNotification save(StoredNotification notification) {
    try {
      jdbc.update(
          """
          INSERT INTO notification (
            id, org_id, created_at, correlation_id, template_key, recipient_subject, locale,
            variables, channel, read_at, source, entity_type, entity_id, dedupe_key
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
          """,
          notification.id(),
          OrganizationContext.current(),
          Timestamp.from(notification.createdAt()),
          notification.correlationId(),
          notification.templateKey(),
          notification.recipientSubject(),
          notification.locale(),
          writeJson(notification.variables()),
          notification.channel().name(),
          notification.readAt() == null ? null : Timestamp.from(notification.readAt()),
          notification.source(),
          notification.entityType(),
          notification.entityId(),
          notification.dedupeKey()
      );
      return notification;
    } catch (DuplicateKeyException ex) {
      if (notification.dedupeKey() == null) {
        throw ex;
      }
      return findByDedupe(notification.recipientSubject(), notification.dedupeKey())
          .orElseThrow(() -> ex);
    }
  }

  @Override
  public List<StoredNotification> listForRecipient(
      String recipientSubject,
      int limit,
      int offset,
      boolean unreadOnly
  ) {
    if (recipientSubject == null || recipientSubject.isBlank() || limit <= 0) {
      return List.of();
    }
    int cap = Math.min(limit, 100);
    int off = Math.max(offset, 0);
    String sql =
        """
        SELECT id, created_at, correlation_id, template_key, recipient_subject, locale,
               variables, channel, read_at, source, entity_type, entity_id, dedupe_key
        FROM notification
        WHERE org_id = ? AND recipient_subject = ?
        """
            + (unreadOnly ? " AND read_at IS NULL" : "")
            + """
         ORDER BY created_at DESC, id DESC
        LIMIT ? OFFSET ?
        """;
    return jdbc.query(sql, (rs, i) -> mapRow(rs), OrganizationContext.current(), recipientSubject, cap, off);
  }

  @Override
  public Optional<StoredNotification> findById(UUID id) {
    List<StoredNotification> rows = jdbc.query(
        """
        SELECT id, created_at, correlation_id, template_key, recipient_subject, locale,
               variables, channel, read_at, source, entity_type, entity_id, dedupe_key
        FROM notification
        WHERE id = ? AND org_id = ?
        """,
        (rs, i) -> mapRow(rs),
        id, OrganizationContext.current()
    );
    return rows.stream().findFirst();
  }

  @Override
  public boolean markRead(UUID id, String recipientSubject, Instant readAt) {
    int updated = jdbc.update(
        """
        UPDATE notification
        SET read_at = ?
        WHERE id = ?
          AND org_id = ?
          AND recipient_subject = ?
          AND read_at IS NULL
        """,
        Timestamp.from(readAt),
        id,
        OrganizationContext.current(),
        recipientSubject
    );
    return updated > 0;
  }

  @Override
  public int markAllRead(String recipientSubject, Instant readAt) {
    return jdbc.update(
        """
        UPDATE notification
        SET read_at = ?
        WHERE recipient_subject = ?
          AND org_id = ?
          AND read_at IS NULL
        """,
        Timestamp.from(readAt),
        recipientSubject,
        OrganizationContext.current()
    );
  }

  @Override
  public long countUnread(String recipientSubject) {
    Long count = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM notification
        WHERE org_id = ? AND recipient_subject = ? AND read_at IS NULL
        """,
        Long.class,
        OrganizationContext.current(), recipientSubject
    );
    return count == null ? 0L : count;
  }

  @Override
  public int deleteOlderThan(Instant cutoff) {
    return jdbc.update(
        "DELETE FROM notification WHERE org_id = ? AND created_at < ?",
        OrganizationContext.current(), Timestamp.from(cutoff)
    );
  }

  private Optional<StoredNotification> findByDedupe(String recipient, String dedupeKey) {
    List<StoredNotification> rows = jdbc.query(
        """
        SELECT id, created_at, correlation_id, template_key, recipient_subject, locale,
               variables, channel, read_at, source, entity_type, entity_id, dedupe_key
        FROM notification
        WHERE org_id = ? AND recipient_subject = ? AND dedupe_key = ?
        """,
        (rs, i) -> mapRow(rs),
        OrganizationContext.current(), recipient,
        dedupeKey
    );
    return rows.stream().findFirst();
  }

  private StoredNotification mapRow(ResultSet rs) throws SQLException {
    Timestamp readTs = rs.getTimestamp("read_at");
    Timestamp created = rs.getTimestamp("created_at");
    String channelRaw = rs.getString("channel");
    NotificationRequest.Channel channel;
    try {
      channel = NotificationRequest.Channel.valueOf(channelRaw);
    } catch (RuntimeException ex) {
      channel = NotificationRequest.Channel.IN_APP;
    }
    return new StoredNotification(
        rs.getObject("id", UUID.class),
        created == null ? Instant.now() : created.toInstant(),
        rs.getObject("correlation_id", UUID.class),
        rs.getString("template_key"),
        rs.getString("recipient_subject"),
        rs.getString("locale"),
        readJson(rs.getString("variables")),
        channel,
        readTs == null ? null : readTs.toInstant(),
        rs.getString("source"),
        rs.getString("entity_type"),
        rs.getString("entity_id"),
        rs.getString("dedupe_key")
    );
  }

  private String writeJson(Map<String, Object> value) {
    try {
      return json.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize notification variables", ex);
    }
  }

  private Map<String, Object> readJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return Map.of();
    }
    try {
      return json.readValue(raw, MAP_TYPE);
    } catch (JsonProcessingException ex) {
      return Map.of();
    }
  }
}
