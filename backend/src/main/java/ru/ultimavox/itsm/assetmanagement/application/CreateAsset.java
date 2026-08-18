package ru.ultimavox.itsm.assetmanagement.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.assetmanagement.domain.Asset;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Service
public class CreateAsset {
  private final JdbcTemplate jdbc;
  private final AssetQuery query;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final AssetSearchIndexer searchIndexer;

  public CreateAsset(
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
  public Asset create(Command command, String actor) {
    if (command.assetTag() == null || command.assetTag().isBlank()) {
      throw new IllegalArgumentException("assetTag is required");
    }
    Asset.Kind kind = command.kind() == null ? Asset.Kind.OTHER : command.kind();
    Asset.Status status = command.status() == null ? Asset.Status.IN_STOCK : command.status();
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    jdbc.update(
        """
        INSERT INTO asset (id, org_id, asset_tag, name, kind, status, owner_subject, configuration_item_id,
            acquired_on, warranty_until, location, created_at, updated_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        id,
        OrganizationContext.current(),
        command.assetTag().trim(),
        command.name(),
        kind.name(),
        status.name(),
        command.ownerSubject(),
        command.configurationItemId(),
        command.acquiredOn() == null ? null : java.sql.Date.valueOf(command.acquiredOn()),
        command.warrantyUntil() == null ? null : java.sql.Date.valueOf(command.warrantyUntil()),
        command.location(),
        Timestamp.from(now),
        Timestamp.from(now)
    );
    Map<String, Object> after = Map.of(
        "assetTag", command.assetTag().trim(),
        "kind", kind.name(),
        "status", status.name()
    );
    audit.append(new AuditTrail.Entry(
        actor, "asset.created", "asset", id.toString(), Map.of(), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "asset.created", 1, now, correlationId, "asset", id.toString(), after
    ));
    Asset saved = query.findById(id).orElseThrow(() -> new IllegalStateException("Asset not readable after create"));
    searchIndexer.index(saved);
    return saved;
  }

  public record Command(
      String assetTag,
      String name,
      Asset.Kind kind,
      Asset.Status status,
      String ownerSubject,
      UUID configurationItemId,
      LocalDate acquiredOn,
      LocalDate warrantyUntil,
      String location
  ) {}
}
