package ru.ultimavox.itsm.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;

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
        Map.of("number", "INC-1", "title", "VPN"),
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
        Map.of("number", "INC-2"),
        NotificationRequest.Channel.EMAIL
    ));

    List<StoredNotification> forAgent7 = store.listForRecipient("agent-7", 50);
    assertThat(forAgent7).hasSize(2);
    // newest first
    assertThat(forAgent7.get(0).templateKey()).isEqualTo("work-item.assigned");
    assertThat(forAgent7.get(0).channel()).isEqualTo(NotificationRequest.Channel.EMAIL);
    assertThat(forAgent7.get(1).correlationId()).isEqualTo(correlation);
    assertThat(forAgent7.get(1).variables()).containsEntry("number", "INC-1");

    assertThat(store.listForRecipient("agent-9", 50)).hasSize(1);
    assertThat(store.listForRecipient("unknown", 50)).isEmpty();
  }

  @Test
  void list_caps_at_fifty() {
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
}
