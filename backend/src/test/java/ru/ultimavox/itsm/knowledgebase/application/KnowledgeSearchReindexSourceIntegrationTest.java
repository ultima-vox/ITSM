package ru.ultimavox.itsm.knowledgebase.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.ultimavox.itsm.platform.search.SearchDocument;

@Testcontainers(disabledWithoutDocker = true)
class KnowledgeSearchReindexSourceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static KnowledgeSearchReindexSource source;
  static JdbcTemplate jdbc;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    jdbc = new JdbcTemplate(ds);
    source = new KnowledgeSearchReindexSource(jdbc);
  }

  @Test
  void projectsCurrentPrimaryLocaleRevisionPerArticle() {
    UUID articleId = UUID.randomUUID();
    Instant updatedAt = Instant.parse("2026-03-01T08:00:00Z");
    jdbc.update("""
            INSERT INTO knowledge_article (id, number, status, version, owner_subject, org_id, created_at, updated_at)
            VALUES (?, 'KB-10', 'PUBLISHED', 2, 'alice', 'kb-org', ?, ?)""",
        articleId, Timestamp.from(updatedAt), Timestamp.from(updatedAt));
    jdbc.update("""
            INSERT INTO knowledge_article_revision (id, article_id, version, locale, title, summary, body, author_subject)
            VALUES (?, ?, 1, 'ru', 'Old title', 'old summary', 'old body', 'alice'),
                   (?, ?, 2, 'ru', 'How to reset VPN', 'Short guide', 'Step 1: reboot', 'alice'),
                   (?, ?, 1, 'en', 'Stale title', NULL, 'stale body', 'alice')""",
        UUID.randomUUID(), articleId,
        UUID.randomUUID(), articleId,
        UUID.randomUUID(), articleId);

    List<SearchDocument> page = source.snapshotPage("kb-org", 0, 100);

    assertThat(page).singleElement().satisfies(doc -> {
      assertThat(doc.id()).isEqualTo(articleId.toString());
      assertThat(doc.objectType()).isEqualTo("knowledge-article");
      assertThat(doc.title()).isEqualTo("How to reset VPN");
      assertThat(doc.body()).isEqualTo("Short guide\n\nStep 1: reboot");
      assertThat(doc.scopes()).contains("knowledge-article", "published");
      assertThat(doc.updatedAt()).isEqualTo(updatedAt);
      assertThat(doc.facets()).containsEntry("number", "KB-10")
          .containsEntry("status", "PUBLISHED");
    });
  }

  @Test
  void organizationIdsListsOnlyOrgsWithArticles() {
    jdbc.update("""
            INSERT INTO knowledge_article (id, number, status, version, owner_subject, org_id, created_at, updated_at)
            VALUES (?, 'KB-20', 'DRAFT', 1, 'bob', 'kb-org-2', now(), now())""",
        UUID.randomUUID());

    assertThat(source.organizationIds()).contains("kb-org", "kb-org-2");
  }
}
