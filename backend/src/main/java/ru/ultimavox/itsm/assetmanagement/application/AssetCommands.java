package ru.ultimavox.itsm.assetmanagement.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.assetmanagement.domain.Asset;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Service
public class AssetCommands {
  private final JdbcTemplate jdbc;
  private final AssetQuery query;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final AssetSearchIndexer searchIndexer;

  public AssetCommands(
      JdbcTemplate jdbc,
      AssetQuery query,
      AuditTrail audit,
      IntegrationEventOutbox outbox,
      AssetSearchIndexer searchIndexer
  ) {
    this.jdbc = jdbc;
    this.query = query;
    this.audit = audit;
    this.outbox = outbox;
    this.searchIndexer = searchIndexer;
  }

  @Transactional
  public Asset assign(UUID id, String ownerSubject, long expectedVersion, String actor) {
    Asset current = query.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + id));
    requireVersion(current, expectedVersion);
    Asset updated = current.assignTo(ownerSubject);
    persist(updated, current, actor, "asset.assigned");
    return updated;
  }

  @Transactional
  public Asset transition(UUID id, Asset.Status target, long expectedVersion, String actor) {
    Asset current = query.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + id));
    requireVersion(current, expectedVersion);
    Asset updated = current.transitionTo(target);
    persist(updated, current, actor, "asset.transitioned");
    return updated;
  }

  private void persist(Asset updated, Asset before, String actor, String action) {
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    int changed = jdbc.update(
        """
        UPDATE asset
        SET status = ?, owner_subject = ?, configuration_item_id = ?, version = version + 1, updated_at = ?
        WHERE id = ? AND org_id = ? AND version = ?
        """,
        updated.status().name(),
        updated.ownerSubject(),
        updated.configurationItemId(),
        Timestamp.from(now),
        updated.id(),
        OrganizationContext.current(),
        before.version()
    );
    if (changed == 0) throw new OptimisticLockingFailureException("Asset changed since version " + before.version());
    jdbc.update(
        """
        INSERT INTO asset_lifecycle_history (asset_id, occurred_at, actor_id, from_status, to_status, owner_subject, details)
        VALUES (?,?,?,?,?,?,?::jsonb)
        """,
        updated.id(),
        Timestamp.from(now),
        actor,
        before.status().name(),
        updated.status().name(),
        updated.ownerSubject(),
        "{\"action\":\"" + action + "\"}"
    );
    Map<String, Object> after = Map.of(
        "status", updated.status().name(),
        "ownerSubject", String.valueOf(updated.ownerSubject())
    );
    audit.append(new AuditTrail.Entry(
        actor, action, "asset", updated.id().toString(),
        Map.of("status", before.status().name()), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), action, 1, now, correlationId,
        "asset", updated.id().toString(), after
    ));
    searchIndexer.index(updated);
  }

  private static void requireVersion(Asset asset, long expectedVersion) {
    if (expectedVersion < 0 || asset.version() != expectedVersion) {
      throw new OptimisticLockingFailureException("Asset changed since version " + expectedVersion);
    }
  }
}
