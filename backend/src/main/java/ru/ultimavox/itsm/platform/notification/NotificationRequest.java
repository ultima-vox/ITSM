package ru.ultimavox.itsm.platform.notification;

import java.util.Map;
import java.util.UUID;

/** Notification engine input. Template rendering applies recipient locale and channel preferences. */
public record NotificationRequest(
        UUID correlationId,
        String templateKey,
        String recipientSubject,
        String locale,
        Map<String, Object> variables,
        Channel channel
) {
    public NotificationRequest {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public enum Channel {
        IN_APP, EMAIL, WEBHOOK
    }
}
