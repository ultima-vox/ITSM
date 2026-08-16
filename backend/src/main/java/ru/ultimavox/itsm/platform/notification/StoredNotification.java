package ru.ultimavox.itsm.platform.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Persisted notification row after {@link NotificationService#send}. */
public record StoredNotification(
    UUID id,
    Instant createdAt,
    UUID correlationId,
    String templateKey,
    String recipientSubject,
    String locale,
    Map<String, Object> variables,
    NotificationRequest.Channel channel,
    Instant readAt,
    String source,
    String entityType,
    String entityId,
    String dedupeKey
) {
  public StoredNotification {
    variables = variables == null ? Map.of() : Map.copyOf(variables);
  }

  public boolean unread() {
    return readAt == null;
  }

  static StoredNotification from(NotificationRequest request) {
    Map<String, Object> vars = request.variables();
    String entityId = firstString(vars, "workItemId", "work_item_id", "entityId", "entity_id");
    String entityType = firstString(vars, "entityType", "entity_type");
    if (entityType == null && entityId != null) {
      entityType = "work_item";
    }
    String source = firstString(vars, "source");
    if (source == null) {
      source = "platform";
    }
    String dedupeKey = null;
    if (entityId != null && request.templateKey() != null) {
      dedupeKey = request.templateKey() + ":" + entityId;
    }
    return new StoredNotification(
        UUID.randomUUID(),
        Instant.now(),
        request.correlationId(),
        request.templateKey(),
        request.recipientSubject(),
        request.locale(),
        vars,
        request.channel(),
        null,
        source,
        entityType,
        entityId,
        dedupeKey
    );
  }

  private static String firstString(Map<String, Object> vars, String... keys) {
    if (vars == null) {
      return null;
    }
    for (String key : keys) {
      Object v = vars.get(key);
      if (v != null) {
        String s = String.valueOf(v).trim();
        if (!s.isEmpty() && !"null".equals(s)) {
          return s;
        }
      }
    }
    return null;
  }
}
