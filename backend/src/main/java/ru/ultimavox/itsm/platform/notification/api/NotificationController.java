package ru.ultimavox.itsm.platform.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.notification.NotificationRequest;
import ru.ultimavox.itsm.platform.notification.NotificationStore;
import ru.ultimavox.itsm.platform.notification.StoredNotification;
import ru.ultimavox.itsm.platform.authorization.SelfScopedEndpoint;

/**
 * In-app notification API. Rows are scoped to the authenticated subject.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@SelfScopedEndpoint
@Tag(name = "Platform — Notifications")
class NotificationController {

  private final NotificationStore store;
  private final SseEventBroadcaster broadcaster;

  NotificationController(NotificationStore store, SseEventBroadcaster broadcaster) {
    this.store = store;
    this.broadcaster = broadcaster;
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "SSE stream of real-time domain events for the authenticated user")
  SseEmitter stream(Authentication authentication) {
    String actor = requireActor(authentication);
    String orgId = OrganizationContext.current();
    String key = orgId + ":" + actor;
    SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
    broadcaster.register(key, emitter);
    return emitter;
  }

  @GetMapping
  @Operation(summary = "List in-app notifications for the authenticated actor")
  NotificationListResponse list(
      Authentication authentication,
      @RequestParam(required = false, defaultValue = "50") int limit,
      @RequestParam(required = false, defaultValue = "0") int offset,
      @RequestParam(required = false, defaultValue = "false") boolean unreadOnly
  ) {
    String actor = requireActor(authentication);
    int safeLimit = Math.min(Math.max(limit, 1), 100);
    int safeOffset = Math.max(offset, 0);
    List<NotificationView> items = store.listForRecipient(actor, safeLimit, safeOffset, unreadOnly)
        .stream()
        .map(NotificationView::from)
        .toList();
    long unread = store.countUnread(actor);
    return new NotificationListResponse(items, unread, safeLimit, safeOffset);
  }

  @PostMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Mark a single notification as read")
  void markRead(Authentication authentication, @PathVariable UUID id) {
    String actor = requireActor(authentication);
    StoredNotification existing = store.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "notification not found"));
    if (!actor.equals(existing.recipientSubject())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "notification not found");
    }
    store.markRead(id, actor, Instant.now());
  }

  @PostMapping("/read-all")
  @Operation(summary = "Mark all notifications as read for the authenticated actor")
  MarkAllReadResponse markAllRead(Authentication authentication) {
    String actor = requireActor(authentication);
    int updated = store.markAllRead(actor, Instant.now());
    return new MarkAllReadResponse(updated);
  }

  private static String requireActor(Authentication authentication) {
    if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }
    return authentication.getName();
  }

  record NotificationListResponse(
      List<NotificationView> items,
      long unreadCount,
      int limit,
      int offset
  ) {}

  record MarkAllReadResponse(int updated) {}

  record NotificationView(
      UUID id,
      Instant createdAt,
      UUID correlationId,
      String templateKey,
      String recipientSubject,
      String locale,
      Map<String, Object> variables,
      NotificationRequest.Channel channel,
      Instant readAt,
      boolean unread,
      String source,
      String entityType,
      String entityId
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
          n.channel(),
          n.readAt(),
          n.unread(),
          n.source(),
          n.entityType(),
          n.entityId()
      );
    }
  }
}
