package ru.ultimavox.itsm.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoggingNotificationServiceTest {

  private InMemoryNotificationStore store;
  private LoggingNotificationService service;

  @BeforeEach
  void setUp() {
    store = new InMemoryNotificationStore();
    service = new LoggingNotificationService(store);
  }

  @Test
  void send_logs_and_retains_for_recipient_list() {
    UUID correlation = UUID.fromString("11111111-1111-1111-1111-111111111111");
    service.send(new NotificationRequest(
        correlation,
        "work-item.assigned",
        "agent-7",
        "ru",
        Map.of("number", "INC-1", "title", "VPN", "workItemId", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        NotificationRequest.Channel.IN_APP
    ));
    service.send(new NotificationRequest(
        UUID.randomUUID(),
        "work-item.transitioned",
        "agent-9",
        "en",
        Map.of("toState", "IN_PROGRESS"),
        NotificationRequest.Channel.IN_APP
    ));
    service.send(new NotificationRequest(
        UUID.randomUUID(),
        "work-item.assigned",
        "agent-7",
        "ru",
        Map.of("number", "INC-2", "workItemId", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
        NotificationRequest.Channel.EMAIL
    ));

    List<StoredNotification> forAgent7 = store.listForRecipient("agent-7", 50);
    assertThat(forAgent7).hasSize(2);
    // newest first
    assertThat(forAgent7.get(0).templateKey()).isEqualTo("work-item.assigned");
    assertThat(forAgent7.get(0).channel()).isEqualTo(NotificationRequest.Channel.EMAIL);
    assertThat(forAgent7.get(1).correlationId()).isEqualTo(correlation);
    assertThat(forAgent7.get(1).variables()).containsEntry("number", "INC-1");
    assertThat(forAgent7.get(1).entityId()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    assertThat(forAgent7.get(1).dedupeKey()).isEqualTo(
        "work-item.assigned:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );

    assertThat(store.listForRecipient("agent-9", 50)).hasSize(1);
    assertThat(store.listForRecipient("unknown", 50)).isEmpty();
  }

  @Test
  void list_caps_at_limit() {
    for (int i = 0; i < 60; i++) {
      service.send(new NotificationRequest(
          UUID.randomUUID(),
          "t",
          "actor-a",
          "ru",
          Map.of("i", i),
          NotificationRequest.Channel.IN_APP
      ));
    }
    assertThat(store.listForRecipient("actor-a", 50)).hasSize(50);
    assertThat(store.listForRecipient("actor-a", 10)).hasSize(10);
  }

  @Test
  void dedupe_skips_duplicate_template_entity_for_recipient() {
    String wi = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    service.send(new NotificationRequest(
        UUID.randomUUID(),
        "work-item.assigned",
        "agent-7",
        "ru",
        Map.of("workItemId", wi, "number", "INC-1"),
        NotificationRequest.Channel.IN_APP
    ));
    service.send(new NotificationRequest(
        UUID.randomUUID(),
        "work-item.assigned",
        "agent-7",
        "ru",
        Map.of("workItemId", wi, "number", "INC-1"),
        NotificationRequest.Channel.IN_APP
    ));
    assertThat(store.listForRecipient("agent-7", 50)).hasSize(1);
  }

  @Test
  void mark_read_and_unread_filter() {
    service.send(new NotificationRequest(
        UUID.randomUUID(),
        "work-item.assigned",
        "agent-7",
        "ru",
        Map.of("workItemId", "dddddddd-dddd-dddd-dddd-dddddddddddd"),
        NotificationRequest.Channel.IN_APP
    ));
    StoredNotification n = store.listForRecipient("agent-7", 1).get(0);
    assertThat(n.unread()).isTrue();
    assertThat(store.countUnread("agent-7")).isEqualTo(1);

    assertThat(store.markRead(n.id(), "agent-7", Instant.parse("2026-01-01T00:00:00Z"))).isTrue();
    assertThat(store.countUnread("agent-7")).isZero();
    assertThat(store.listForRecipient("agent-7", 10, 0, true)).isEmpty();
    assertThat(store.listForRecipient("agent-7", 10, 0, false)).hasSize(1);
    assertThat(store.listForRecipient("agent-7", 10, 0, false).get(0).readAt()).isNotNull();
  }

  @Test
  void mark_all_read() {
    service.send(new NotificationRequest(
        UUID.randomUUID(), "a", "actor-b", "ru", Map.of("i", 1), NotificationRequest.Channel.IN_APP
    ));
    service.send(new NotificationRequest(
        UUID.randomUUID(), "b", "actor-b", "ru", Map.of("i", 2), NotificationRequest.Channel.IN_APP
    ));
    assertThat(store.markAllRead("actor-b", Instant.now())).isEqualTo(2);
    assertThat(store.countUnread("actor-b")).isZero();
  }

  @Test
  void retention_deletes_old_rows() {
    StoredNotification old = new StoredNotification(
        UUID.randomUUID(),
        Instant.parse("2020-01-01T00:00:00Z"),
        UUID.randomUUID(),
        "old",
        "actor-c",
        "ru",
        Map.of(),
        NotificationRequest.Channel.IN_APP,
        null,
        "platform",
        null,
        null,
        null
    );
    store.save(old);
    service.send(new NotificationRequest(
        UUID.randomUUID(), "new", "actor-c", "ru", Map.of(), NotificationRequest.Channel.IN_APP
    ));
    int deleted = store.deleteOlderThan(Instant.parse("2025-01-01T00:00:00Z"));
    assertThat(deleted).isEqualTo(1);
    assertThat(store.listForRecipient("actor-c", 10)).hasSize(1);
    assertThat(store.listForRecipient("actor-c", 10).get(0).templateKey()).isEqualTo("new");
  }
}
