package ru.ultimavox.itsm.platform.automation;

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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Testcontainers(disabledWithoutDocker = true)
class AutomationRuleAdminIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static AutomationRuleAdminService admin;

    @BeforeAll
    static void setup() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        var json = new ObjectMapper();
        admin = new AutomationRuleAdminService(new JdbcAutomationRuleRepository(new JdbcTemplate(ds), json), json,
                mock(AuditTrail.class), mock(IntegrationEventOutbox.class));
    }

    @Test
    void createsAndUpdatesWithOptimisticVersion() {
        OrganizationContext.runAs("automation-admin-" + UUID.randomUUID(), () -> {
            String key = "custom.rule." + UUID.randomUUID().toString().substring(0, 8);
            AutomationRule created = admin.create("admin", command(key, "Initial"));
            assertThat(created.version()).isEqualTo(1);
            AutomationRule updated = admin.update("admin", created.id(), 1, command(key, "Updated"));
            assertThat(updated.version()).isEqualTo(2);
            assertThat(updated.name()).isEqualTo("Updated");
            assertThatThrownBy(() -> admin.update("admin", updated.id(), 1, command(key, "Stale")))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
            return null;
        });
    }

    @Test
    void rejectsUnknownAndDuplicateActionTypes() {
        var duplicate = new AutomationRuleAdminService.Command("invalid.actions", "Invalid", false,
                new AutomationRule.Trigger("work-item.created"), List.of(),
                List.of(new AutomationRule.Action("log", Map.of()), new AutomationRule.Action("log", Map.of())));
        assertThatThrownBy(() -> OrganizationContext.runAs("automation-validation", () -> admin.create("admin", duplicate)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate action type");
    }

    private static AutomationRuleAdminService.Command command(String key, String name) {
        return new AutomationRuleAdminService.Command(key, name, false,
                new AutomationRule.Trigger("work-item.created"),
                List.of(new AutomationRule.Condition("priority", AutomationRule.Operator.EQUALS, "critical")),
                List.of(new AutomationRule.Action("log", Map.of("message", "critical"))));
    }
}
