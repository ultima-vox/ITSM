package ru.ultimavox.itsm.platform.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.event.DomainEvent;

class ActionParameterResolverTest {

  private final DomainEvent event = new DomainEvent(
      UUID.randomUUID(), "incident.created", 1, Instant.now(), UUID.randomUUID(), null,
      "org-42", "alice", "work-item", "11111111-1111-1111-1111-111111111111",
      Map.<String, Object>of("requesterId", "bob", "service", "Print"));

  @Test
  void returnsLiteralValueWhenNoPlaceholder() {
    assertThat(resolve("assigneeId", "support-team"))
        .isEqualTo("support-team");
  }

  @Test
  void resolvesDataPlaceholderFromEventPayload() {
    assertThat(resolve("assigneeId", "{{data.requesterId}}")).isEqualTo("bob");
    assertThat(resolve("assigneeId", "{{ data.service }}")).isEqualTo("Print");
  }

  @Test
  void resolvesEventPlaceholders() {
    assertThat(resolve("assigneeId", "{{event.actorId}}")).isEqualTo("alice");
    assertThat(resolve("assigneeId", "{{event.organizationId}}")).isEqualTo("org-42");
    assertThat(resolve("assigneeId", "{{event.aggregateId}}"))
        .isEqualTo("11111111-1111-1111-1111-111111111111");
    assertThat(resolve("assigneeId", "{{event.type}}")).isEqualTo("incident.created");
  }

  @Test
  void unknownPlaceholderAndMissingDataResolveToNull() {
    assertThat(resolve("assigneeId", "{{data.missing}}")).isNull();
    assertThat(resolve("assigneeId", "{{event.unknown}}")).isNull();
  }

  @Test
  void missingParameterResolvesToNull() {
    assertThat(ActionParameterResolver.resolve(Map.of(), event, "assigneeId")).isNull();
  }

  private String resolve(String key, String value) {
    return ActionParameterResolver.resolve(Map.of(key, value), event, key);
  }
}
