package ru.ultimavox.itsm.platform.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.metadata.AttributeDefinition.AttributeType;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Testcontainers(disabledWithoutDocker = true)
class ObjectDefinitionAdminIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
  static ObjectDefinitionAdminService admin;
  static JdbcObjectDefinitionRepository repository;

  @BeforeAll
  static void setup() {
    var dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
    repository = new JdbcObjectDefinitionRepository(new JdbcTemplate(dataSource), new ObjectMapper());
    admin = new ObjectDefinitionAdminService(
        repository, mock(AuditTrail.class), mock(IntegrationEventOutbox.class));
  }

  @Test
  void createsImmutableVersionsAndPublishesCompatibleSchema() {
    String org = "metadata-admin-" + UUID.randomUUID();
    String key = "custom-record-" + UUID.randomUUID().toString().substring(0, 8);
    OrganizationContext.runAs(org, () -> {
      ObjectDefinition draft1 = admin.createDraft("admin", draft(key, List.of(attribute("title", true))));
      assertThat(draft1.version()).isEqualTo(1);
      assertThat(repository.findActiveByKey(key)).isEmpty();
      assertThat(admin.publish("admin", key, 1).version()).isEqualTo(1);

      ObjectDefinition draft2 = admin.createDraft("admin", draft(key,
          List.of(attribute("title", true), attribute("details", false))));
      assertThat(draft2.version()).isEqualTo(2);
      assertThat(repository.findActiveByKey(key).orElseThrow().version()).isEqualTo(1);
      admin.publish("admin", key, 2);

      assertThat(repository.findActiveByKey(key).orElseThrow().version()).isEqualTo(2);
      assertThat(admin.versions(key)).extracting(v -> v.definition().version()).containsExactly(2, 1);
      assertThat(admin.versions(key)).filteredOn(ObjectDefinitionVersion::active).hasSize(1)
          .first().extracting(v -> v.definition().version()).isEqualTo(2);
      return null;
    });
  }

  @Test
  void rejectsBreakingPublicationAndKeepsPriorVersionActive() {
    String org = "metadata-compat-" + UUID.randomUUID();
    String key = "safe-record-" + UUID.randomUUID().toString().substring(0, 8);
    OrganizationContext.runAs(org, () -> {
      admin.createDraft("admin", draft(key, List.of(attribute("title", false))));
      admin.publish("admin", key, 1);
      admin.createDraft("admin", draft(key, List.of(attribute("other", false))));

      assertThatThrownBy(() -> admin.publish("admin", key, 2))
          .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot be removed");
      assertThat(repository.findActiveByKey(key).orElseThrow().version()).isEqualTo(1);
      assertThat(admin.versions(key)).filteredOn(ObjectDefinitionVersion::active).hasSize(1);
      return null;
    });
  }

  @Test
  void enforcesLocalizationAndTenantIsolation() {
    String key = "tenant-record-" + UUID.randomUUID().toString().substring(0, 8);
    assertThatThrownBy(() -> OrganizationContext.runAs("tenant-a", () -> admin.createDraft(
        "admin", new ObjectDefinitionAdminService.Draft(key, Map.of("en", "Only English"),
            List.of(attribute("title", true)), List.of()))))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ru and en");

    OrganizationContext.runAs("tenant-a", () -> {
      admin.createDraft("admin", draft(key, List.of(attribute("title", true))));
      admin.publish("admin", key, 1);
      return null;
    });
    assertThat(OrganizationContext.runAs("tenant-b", () -> repository.findActiveByKey(key))).isEmpty();
  }

  private static ObjectDefinitionAdminService.Draft draft(
      String key, List<AttributeDefinition> attributes) {
    return new ObjectDefinitionAdminService.Draft(key,
        Map.of("ru", "Пользовательский объект", "en", "Custom object"), attributes, List.of());
  }

  private static AttributeDefinition attribute(String key, boolean required) {
    return new AttributeDefinition(key, AttributeType.TEXT, required, true,
        Map.of("ru", "Поле " + key, "en", "Field " + key), List.of());
  }
}
