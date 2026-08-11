package ru.ultimavox.itsm.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Testcontainers
class NotificationPreferenceIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static NotificationPreferenceService service;

    @BeforeAll
    static void setup() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        service = new NotificationPreferenceService(new JdbcTemplate(ds),
                mock(AuditTrail.class), mock(IntegrationEventOutbox.class));
    }

    @Test
    void persistsPerOrganizationAndSubjectWithDefaults() {
        var custom = new NotificationPreferenceService.Preferences(false, true, false, true, false);
        OrganizationContext.runAs("org-a", () -> {
            assertThat(service.get("alice").email()).isTrue();
            assertThat(service.save("alice", custom)).isEqualTo(custom);
            assertThat(service.get("alice")).isEqualTo(custom);
            assertThat(service.allows(new NotificationRequest(UUID.randomUUID(), "sla.breached", "alice",
                    "en", Map.of(), NotificationRequest.Channel.IN_APP))).isFalse();
            assertThat(service.allows(new NotificationRequest(UUID.randomUUID(), "work-item.updated", "alice",
                    "en", Map.of(), NotificationRequest.Channel.EMAIL))).isFalse();
            assertThat(service.allows(new NotificationRequest(UUID.randomUUID(), "work-item.updated", "alice",
                    "en", Map.of(), NotificationRequest.Channel.IN_APP))).isTrue();
            return null;
        });
        OrganizationContext.runAs("org-b", () -> {
            assertThat(service.get("alice")).isNotEqualTo(custom);
            assertThat(service.get("alice").email()).isTrue();
            return null;
        });
    }
}
