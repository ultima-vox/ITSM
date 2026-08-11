package ru.ultimavox.itsm.platform.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
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

@Testcontainers
class SlaPolicyAdminIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static SlaPolicyAdminService admin;
    static SlaPolicyRepository repository;

    @BeforeAll
    static void setup() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        repository = new JdbcSlaPolicyRepository(new JdbcTemplate(ds), new ObjectMapper());
        admin = new SlaPolicyAdminService(repository, mock(AuditTrail.class), mock(IntegrationEventOutbox.class));
    }

    @Test
    void tenantOverrideUsesOptimisticVersionAndRejectsStaleWrite() {
        OrganizationContext.runAs("sla-admin-" + UUID.randomUUID(), () -> {
            var source = repository.listAll().stream().filter(view -> view.policy().key().equals("work-item.response"))
                    .findFirst().orElseThrow();
            var targets = List.of(new SlaPolicy.Target("response", "priority=CRITICAL",
                    Duration.ofMinutes(10), Duration.ofMinutes(2)));
            var updated = admin.update("admin", source.policy().id(), source.version(), true, targets);
            assertThat(updated.version()).isEqualTo(source.version() + 1);
            assertThat(updated.policy().targets()).hasSize(1);
            assertThatThrownBy(() -> admin.update("admin", updated.policy().id(), source.version(), false, targets))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
            return null;
        });
    }
}
