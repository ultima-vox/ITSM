package ru.ultimavox.itsm.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoggingAiGatewayTest {
  private final LoggingAiGateway gateway = new LoggingAiGateway();

  @Test
  void summarize_marks_human_review_and_never_empty() {
    var prompt = new AiGateway.AuthorizedPrompt(
        "user-1", Set.of("ai.summarize"), "summarize", "VPN is down for remote staff", UUID.randomUUID(), 256
    );
    var suggestion = gateway.summarize(prompt);
    assertThat(suggestion.requiresHumanReview()).isTrue();
    assertThat(suggestion.content()).containsIgnoringCase("summary");
    assertThat(suggestion.provider()).isEqualTo("logging-stub");
  }

  @Test
  void suggest_resolution_is_advisory_only() {
    var prompt = new AiGateway.AuthorizedPrompt(
        "user-1", Set.of("ai.suggest"), "suggest-resolution", "DB latency spike", UUID.randomUUID(), 512
    );
    var suggestion = gateway.suggestResolution(prompt);
    assertThat(suggestion.requiresHumanReview()).isTrue();
    assertThat(suggestion.content()).contains("Suggested resolution");
  }
}
