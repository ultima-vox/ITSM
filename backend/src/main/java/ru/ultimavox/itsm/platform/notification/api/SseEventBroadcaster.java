package ru.ultimavox.itsm.platform.notification.api;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.ultimavox.itsm.platform.event.DomainEventEnvelope;

/**
 * Broadcasts domain events to connected SSE clients.
 * Each client registers via SseEmitter; this component listens for DomainEventEnvelope
 * and pushes relevant events to the authenticated user's emitter.
 */
@Component
class SseEventBroadcaster {
  private static final Logger log = LoggerFactory.getLogger(SseEventBroadcaster.class);

  /** Per-user SSE emitters. Key = "orgId:subjectId". */
  private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

  void register(String key, SseEmitter emitter) {
    emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
    emitter.onCompletion(() -> remove(key, emitter));
    emitter.onTimeout(() -> remove(key, emitter));
    emitter.onError(e -> remove(key, emitter));
  }

  private void remove(String key, SseEmitter emitter) {
    var list = emitters.get(key);
    if (list != null) {
      list.remove(emitter);
      if (list.isEmpty()) emitters.remove(key);
    }
  }

  @EventListener
  void onDomainEvent(DomainEventEnvelope envelope) {
    var event = envelope.event();
    String orgId = event.organizationId();
    if (orgId == null) orgId = "default";
    String actorId = event.actorId();

    // Broadcast to all emitters in the same org (except the actor to avoid echo)
    String pattern = orgId + ":";
    for (var entry : emitters.entrySet()) {
      if (!entry.getKey().startsWith(pattern)) continue;
      String subject = entry.getKey().substring(pattern.length());
      if (subject.equals(actorId)) continue;

      Map<String, Object> data = Map.of(
          "type", event.type(),
          "aggregateType", event.aggregateType() != null ? event.aggregateType() : "",
          "aggregateId", event.aggregateId() != null ? event.aggregateId() : "",
          "occurredAt", event.occurredAt() != null ? event.occurredAt().toString() : Instant.now().toString(),
          "actorId", actorId != null ? actorId : ""
      );

      for (SseEmitter emitter : entry.getValue()) {
        try {
          emitter.send(SseEmitter.event()
              .name("domain-event")
              .data(data));
        } catch (IOException | IllegalStateException e) {
          log.debug("SSE send failed for {}: {}", entry.getKey(), e.getMessage());
          remove(entry.getKey(), emitter);
        }
      }
    }
  }

  int emitterCount() {
    return emitters.values().stream().mapToInt(CopyOnWriteArrayList::size).sum();
  }
}
