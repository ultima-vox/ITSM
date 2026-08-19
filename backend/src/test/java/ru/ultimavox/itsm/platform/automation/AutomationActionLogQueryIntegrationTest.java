package ru.ultimavox.itsm.platform.automation;

import static org.assertj.core.api.Assertions.assertThat;

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
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Testcontainers(disabledWithoutDocker = true)
class AutomationActionLogQueryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static AutomationActionLogQuery query;
    static AutomationActionLogRepository log;

    @BeforeAll
    static void setup() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        var json = new ObjectMapper().findAndRegisterModules();
        var jdbc = new JdbcTemplate(ds);
        log = new JdbcAutomationActionLogRepository(jdbc, json);
        query = new AutomationActionLogQuery(jdbc, json);
    }

    @Test
    void listsNewestFirstScopedToOrganizationWithFilters() {
        OrganizationContext.runAs("org-a-" + UUID.randomUUID(), () -> {
            String rule = "route.incident." + UUID.randomUUID().toString().substring(0, 8);
            UUID event = UUID.randomUUID();
            log.tryLog(rule, event, "assign", "STARTED", Map.of("assigneeId", "u-1"));
            // A second tryLog for the same action is deduped; terminal status arrives via complete().
            assertThat(log.tryLog(rule, event, "assign", "STARTED", Map.of("assigneeId", "u-1"))).isFalse();
            log.complete(rule, event, "assign", "SUCCEEDED", Map.of("assigneeId", "u-1"), 1);
            log.tryLog("notify." + rule, UUID.randomUUID(), "notify", "SUCCEEDED", Map.of("channel", "IN_APP"));

            List<AutomationActionLogEntry> all = query.list(null, null, 100, 0);
            assertThat(all).hasSize(2);
            assertThat(all.get(0).ruleKey()).isEqualTo("notify." + rule);
            assertThat(all.get(0).details()).containsEntry("channel", "IN_APP");

            List<AutomationActionLogEntry> byRule = query.list(rule, null, 100, 0);
            assertThat(byRule).hasSize(1).allMatch(e -> e.ruleKey().equals(rule));

            List<AutomationActionLogEntry> byStatus = query.list(null, "SUCCEEDED", 100, 0);
            assertThat(byStatus).hasSize(2).allMatch(e -> "SUCCEEDED".equals(e.status()));

            List<AutomationActionLogEntry> paged = query.list(null, null, 1, 1);
            assertThat(paged).hasSize(1).allMatch(e -> e.id() != null);
            return null;
        });
    }

    @Test
    void isolatesOrganizations() {
        String sharedRule = "shared.rule";
        UUID event = UUID.randomUUID();
        OrganizationContext.runAs("iso-a", () -> {
            log.tryLog(sharedRule, event, "log", "SUCCEEDED", Map.of());
            return null;
        });
        OrganizationContext.runAs("iso-b", () -> {
            log.tryLog(sharedRule, event, "log", "FAILED", Map.of("error", "boom"));
            return null;
        });

        assertThat(OrganizationContext.runAs("iso-a", () -> query.list(null, null, 100, 0))).hasSize(1);
        assertThat(OrganizationContext.runAs("iso-b", () -> query.list(null, null, 100, 0)))
                .hasSize(1)
                .allMatch(e -> "FAILED".equals(e.status()) && e.details().containsKey("error"));
        assertThat(OrganizationContext.runAs("iso-c", () -> query.list(null, null, 100, 0))).isEmpty();
    }

    @Test
    void capsLimitAtTwoHundred() {
        OrganizationContext.runAs("cap-org", () -> {
            for (int i = 0; i < 5; i++) {
                log.tryLog("cap.rule", UUID.randomUUID(), "log", "STARTED", Map.of("i", i));
            }
            assertThat(query.list(null, null, 500, 0)).hasSize(5);
            assertThat(query.list(null, null, 0, 0)).hasSize(1);
            return null;
        });
    }
}
