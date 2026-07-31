package ru.ultimavox.itsm.assetmanagement.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.assetmanagement.domain.Asset;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Service
public class LinkAssetToCi {
  private final JdbcTemplate jdbc;
  private final AssetQuery assetQuery;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public LinkAssetToCi(JdbcTemplate jdbc, AssetQuery assetQuery, AuditTrail audit, IntegrationEventOutbox outbox) {
    this.jdbc = jdbc;
    this.assetQuery = assetQuery;
    this.audit = audit;
    this.outbox = outbox;
  }

  @Transactional
  public Asset link(UUID assetId, UUID configurationItemId, String actor) {
    Asset current = assetQuery.findById(assetId)
        .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

    Integer ciExists = jdbc.queryForObject(
        "SELECT COUNT(*) FROM configuration_item WHERE id = ?",
        Integer.class,
        configurationItemId
    );
    if (ciExists == null || ciExists == 0) {
      throw new IllegalArgumentException("Configuration item not found: " + configurationItemId);
    }

    Asset linked = current.linkToCi(configurationItemId);
    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();

    jdbc.update(
        "UPDATE asset SET configuration_item_id = ?, updated_at = ? WHERE id = ?",
        linked.configurationItemId(), now, linked.id()
    );
    jdbc.update(
        """
            INSERT INTO asset_lifecycle_history (asset_id, occurred_at, actor_id, from_status, to_status, owner_subject, details)
            VALUES (?,?,?,?,?,?,?::jsonb)
            """,
        linked.id(), now, actor, current.status().name(), linked.status().name(), linked.ownerSubject(),
        "{\"action\":\"link-ci\",\"configurationItemId\":\"" + configurationItemId + "\"}"
    );

    Map<String, Object> before = Map.of(
        "configurationItemId", String.valueOf(current.configurationItemId()),
        "status", current.status().name()
    );
    Map<String, Object> after = Map.of(
        "configurationItemId", configurationItemId.toString(),
        "status", linked.status().name()
    );
    audit.append(new AuditTrail.Entry(
        actor, "asset.linked-ci", "asset", assetId.toString(), before, after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "asset.linked-ci", 1, now, correlationId,
        "asset", assetId.toString(), after
    ));
    return linked;
  }
}
