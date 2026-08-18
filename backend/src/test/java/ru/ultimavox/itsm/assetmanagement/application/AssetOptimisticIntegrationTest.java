package ru.ultimavox.itsm.assetmanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.ultimavox.itsm.assetmanagement.domain.Asset;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Testcontainers(disabledWithoutDocker = true)
class AssetOptimisticIntegrationTest {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
  static CreateAsset create;
  static AssetCommands commands;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    var query = new AssetQuery(new JdbcTemplate(ds));
    var audit = mock(AuditTrail.class);
    var outbox = mock(IntegrationEventOutbox.class);
    var indexer = mock(AssetSearchIndexer.class);
    create = new CreateAsset(new JdbcTemplate(ds), query, audit, outbox, indexer);
    commands = new AssetCommands(new JdbcTemplate(ds), query, audit, outbox, indexer);
  }

  @Test
  void rejectsStaleLifecycleAndAssignmentWrites() {
    OrganizationContext.runAs("asset-version-" + UUID.randomUUID(), () -> {
      Asset asset = create.create(new CreateAsset.Command(
          "AST-" + UUID.randomUUID(), "Laptop", Asset.Kind.LAPTOP, Asset.Status.IN_STOCK,
          null, null, null, null, null), "alice");
      assertThat(asset.version()).isZero();

      Asset active = commands.transition(asset.id(), Asset.Status.IN_USE, 0, "alice");
      assertThat(active.version()).isEqualTo(1);
      assertThatThrownBy(() -> commands.assign(asset.id(), "bob", 0, "alice"))
          .isInstanceOf(OptimisticLockingFailureException.class);
      Asset assigned = commands.assign(asset.id(), "bob", 1, "alice");
      assertThat(assigned.version()).isEqualTo(2);
      assertThat(assigned.ownerSubject()).isEqualTo("bob");
      return null;
    });
  }
}
