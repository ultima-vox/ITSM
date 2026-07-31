package ru.ultimavox.itsm.platform.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.notification.InMemoryNotificationStore;
import ru.ultimavox.itsm.platform.notification.NotificationRequest;
import ru.ultimavox.itsm.platform.notification.StoredNotification;

/**
 * Demo REST surface for in-app notifications (in-memory, last 50 for actor).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Platform — Notifications")
class NotificationController {

  private final InMemoryNotificationStore store;

  NotificationController(InMemoryNotificationStore store) {
    this.store = store;
  }

  @GetMapping
  @Operation(summary = "List recent in-app notifications for the authenticated actor (demo in-memory)")
  List<NotificationView> list(
      Authentication authentication,
      @RequestParam(required = false, defaultValue = "50") int limit
  ) {
    String actor = authentication != null ? authentication.getName() : null;
    return store.listForRecipient(actor, limit).stream()
        .map(NotificationView::from)
        .toList();
  }

  record NotificationView(
      UUID id,
      Instant createdAt,
      UUID correlationId,
      String templateKey,
      String recipientSubject,
      String locale,
      Map<String, Object> variables,
      NotificationRequest.Channel channel
  ) {
    static NotificationView from(StoredNotification n) {
      return new NotificationView(
          n.id(),
          n.createdAt(),
          n.correlationId(),
          n.templateKey(),
          n.recipientSubject(),
          n.locale(),
          n.variables(),
          n.channel()
      );
    }
  }
}
