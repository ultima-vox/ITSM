package ru.ultimavox.itsm.knowledgebase.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.knowledgebase.domain.KnowledgeArticle.Content;
import ru.ultimavox.itsm.knowledgebase.domain.KnowledgeArticle.Status;

class KnowledgeArticleTest {

  @Test
  void publish_requires_russian_content() {
    KnowledgeArticle draft = new KnowledgeArticle(
        UUID.randomUUID(),
        "KB-1",
        "vpn",
        Status.IN_REVIEW,
        1,
        "agent",
        Map.of("en", new Content("VPN", "body", "sum")),
        null
    );
    assertThatThrownBy(() -> draft.publish(Instant.parse("2026-01-01T00:00:00Z")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Russian");
  }

  @Test
  void publish_from_review_ok() {
    KnowledgeArticle draft = new KnowledgeArticle(
        UUID.randomUUID(),
        "KB-1",
        "vpn",
        Status.IN_REVIEW,
        1,
        "agent",
        Map.of("ru", new Content("VPN", "текст", "кратко")),
        null
    );
    KnowledgeArticle pub = draft.publish(Instant.parse("2026-06-01T00:00:00Z"));
    assertThat(pub.status()).isEqualTo(Status.PUBLISHED);
    assertThat(pub.version()).isEqualTo(2);
  }
}
