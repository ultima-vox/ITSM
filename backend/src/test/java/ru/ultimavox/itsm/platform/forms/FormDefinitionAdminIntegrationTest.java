package ru.ultimavox.itsm.platform.forms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import ru.ultimavox.itsm.platform.metadata.AttributeDefinition;
import ru.ultimavox.itsm.platform.metadata.ObjectDefinition;
import ru.ultimavox.itsm.platform.metadata.ObjectDefinitionService;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Testcontainers(disabledWithoutDocker = true)
class FormDefinitionAdminIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static FormDefinitionAdminService admin;

    @BeforeAll
    static void setup() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var json = new ObjectMapper();
        var repository = new JdbcFormDefinitionRepository(jdbc, json);
        ObjectDefinitionService objects = mock(ObjectDefinitionService.class);
        when(objects.requireActiveByKey("work-item")).thenReturn(new ObjectDefinition(UUID.randomUUID(),
                "work-item", 1, Map.of("ru", "Обращение", "en", "Work item"),
                Map.of("title", new AttributeDefinition("title", AttributeDefinition.AttributeType.TEXT,
                        true, true, Map.of("ru", "Заголовок", "en", "Title"), List.of())), List.of()));
        admin = new FormDefinitionAdminService(repository, new FormDefinitionService(repository, json), objects,
                mock(AuditTrail.class), mock(IntegrationEventOutbox.class));
    }

    @Test
    void createsImmutableDraftsAndAtomicallyPublishesOneVersion() {
        OrganizationContext.runAs("form-admin-" + UUID.randomUUID(), () -> {
            String key = "work-item.custom." + UUID.randomUUID().toString().substring(0, 8);
            assertThat(admin.createDraft("admin", draft(key)).version()).isEqualTo(1);
            assertThat(admin.publish("admin", key, 1).version()).isEqualTo(1);
            assertThat(admin.publish("admin", key, 1).version()).isEqualTo(1);
            assertThat(admin.createDraft("admin", draft(key)).version()).isEqualTo(2);
            admin.publish("admin", key, 2);
            assertThat(admin.versions(key)).hasSize(2).filteredOn(FormDefinitionVersion::active).hasSize(1)
                    .first().extracting(item -> item.definition().version()).isEqualTo(2);
            return null;
        });
    }

    @Test
    void rejectsUnknownAttributesAndMissingLocalization() {
        OrganizationContext.runAs("form-validation-" + UUID.randomUUID(), () -> {
            String key = "work-item.invalid." + UUID.randomUUID().toString().substring(0, 8);
            var badField = new FormDefinition.Field("not-a-real-field", false, null, null);
            var bad = new FormDefinitionAdminService.Draft(key, "work-item",
                    List.of(new FormDefinition.Section("main", Map.of("ru", "Основное", "en", "Main"), List.of(badField))));
            assertThatThrownBy(() -> admin.createDraft("admin", bad))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown object attribute");
            return null;
        });
    }

    private static FormDefinitionAdminService.Draft draft(String key) {
        var field = new FormDefinition.Field("title", true, null, null);
        return new FormDefinitionAdminService.Draft(key, "work-item",
                List.of(new FormDefinition.Section("main", Map.of("ru", "Основное", "en", "Main"), List.of(field))));
    }
}
