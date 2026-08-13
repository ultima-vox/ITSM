package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class WorkItemSearchReindexSourceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static WorkItemSearchReindexSource source;
  static JdbcTemplate jdbc;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    jdbc = new JdbcTemplate(ds);
    source = new WorkItemSearchReindexSource(jdbc);
  }

  @Test
  void projectsAuthoritativeRowsPerOrganization() {
    UUID id = UUID.randomUUID();
    Instant updatedAt = Instant.parse("2026-02-01T10:00:00Z");
    jdbc.update("""
            INSERT INTO work_item (id, number, type, title, description, service, state, priority,
                                   requester_id, org_id, created_at, updated_at)
            VALUES (?, 'IT-1001', 'INCIDENT', 'Printer on fire', 'smoke everywhere',
                    'Print', 'OPEN', 'HIGH', 'user-1', 'reindex-org', ?, ?)""",
        id, Timestamp.from(updatedAt), Timestamp.from(updatedAt));

    List<SearchDocument> page = source.snapshotPage("reindex-org", 0, 100);

    assertThat(page).singleElement().satisfies(doc -> {
      assertThat(doc.id()).isEqualTo(id.toString());
      assertThat(doc.objectType()).isEqualTo("work-item");
      assertThat(doc.title()).isEqualTo("IT-1001 · Printer on fire");
      assertThat(doc.body()).isEqualTo("smoke everywhere");
      assertThat(doc.scopes()).contains("work-item", "incident");
      assertThat(doc.updatedAt()).isEqualTo(updatedAt);
      assertThat(doc.facets()).containsEntry("number", "IT-1001")
          .containsEntry("state", "OPEN")
          .containsEntry("priority", "HIGH")
          .containsEntry("service", "Print");
    });
  }

  @Test
  void organizationIdsListsOnlyOrgsWithWorkItems() {
    jdbc.update("""
            INSERT INTO work_item (id, number, type, title, description, service, state, priority,
                                   requester_id, org_id, created_at, updated_at)
            VALUES (?, 'IT-2001', 'REQUEST', 'New laptop', 'spec attached', 'Procurement',
                    'NEW', 'MEDIUM', 'user-2', 'reindex-org-2', now(), now())""",
        UUID.randomUUID());

    assertThat(source.organizationIds()).contains("reindex-org", "reindex-org-2");
  }
}
