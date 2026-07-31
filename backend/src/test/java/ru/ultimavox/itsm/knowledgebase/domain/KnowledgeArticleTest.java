package ru.ultimavox.itsm.knowledgebase.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeArticleTest {
  private KnowledgeArticle draft(Map<String, KnowledgeArticle.Content> translations) {
    return new KnowledgeArticle(
        UUID.randomUUID(), "KB-12", "sample-article", KnowledgeArticle.Status.DRAFT, 1, "expert-1", translations, null
    );
  }

  @Test
  void publication_requires_primary_locale_content() {
    var article = draft(Map.of("en", new KnowledgeArticle.Content("VPN", "Connect to VPN", ""))).submitForReview();
    assertThatThrownBy(() -> article.publish(Instant.now())).hasMessageContaining("Russian");
  }

  @Test
  void publishing_increments_revision_and_sets_review_date() {
    var article = draft(Map.of("ru", new KnowledgeArticle.Content("VPN", "Инструкция", "")))
        .submitForReview()
        .publish(Instant.parse("2026-12-01T00:00:00Z"));
    assertThat(article.status()).isEqualTo(KnowledgeArticle.Status.PUBLISHED);
    assertThat(article.version()).isEqualTo(2);
    assertThat(article.nextReviewAt()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
  }
}
