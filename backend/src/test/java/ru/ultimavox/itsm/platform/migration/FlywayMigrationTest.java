package ru.ultimavox.itsm.platform.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migratesCleanDatabaseToLatestVersion() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("53");
        assertThat(flyway.info().pending()).isEmpty();
        flyway.validate();
    }

    @Test
    void auditEventsAreAppendOnly() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO audit_event
                        (actor_id, action, object_type, object_id, correlation_id)
                    VALUES
                        ('migration-test', 'created', 'test', '1', gen_random_uuid())
                    """);

            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE audit_event SET action = 'tampered' WHERE object_id = '1'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("audit_event is append-only");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "DELETE FROM audit_event WHERE object_id = '1'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("audit_event is append-only");
        }
    }
}
