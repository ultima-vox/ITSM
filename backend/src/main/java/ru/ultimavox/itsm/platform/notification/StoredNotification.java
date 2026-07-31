package ru.ultimavox.itsm.platform.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** In-memory demo notification row retained after {@link NotificationService#send}. */
public record StoredNotification(
    UUID id,
    Instant createdAt,
    UUID correlationId,
    String templateKey,
    String recipientSubject,
    String locale,
    Map<String, Object> variables,
    NotificationRequest.Channel channel
) {
  public StoredNotification {
    variables = variables == null ? Map.of() : Map.copyOf(variables);
  }

  static StoredNotification from(NotificationRequest request) {
    return new StoredNotification(
        UUID.randomUUID(),
        Instant.now(),
        request.correlationId(),
        request.templateKey(),
        request.recipientSubject(),
        request.locale(),
        request.variables(),
        request.channel()
    );
  }
}
