package ru.ultimavox.itsm.cmdb.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.cmdb.domain.CiRelationship;
import ru.ultimavox.itsm.cmdb.domain.ConfigurationItem;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Service
public class CmdbCommands {
  private final JdbcTemplate jdbc;
  private final CmdbQuery query;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final ObjectMapper json;

  public CmdbCommands(
      JdbcTemplate jdbc,
      CmdbQuery query,
      AuditTrail audit,
      IntegrationEventOutbox outbox,
      ObjectMapper json
  ) {
    this.jdbc = jdbc;
    this.query = query;
    this.audit = audit;
    this.outbox = outbox;
    this.json = json;
  }

  @Transactional
  public ConfigurationItem create(CreateCommand command, String actor) {
    if (command.name() == null || command.name().isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    String classKey = command.classKey() == null || command.classKey().isBlank()
        ? "ci"
        : command.classKey().trim();
    ConfigurationItem.Status status = command.status() == null
        ? ConfigurationItem.Status.OPERATIONAL
        : command.status();
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    Map<String, Object> attrs = command.attributes() == null ? Map.of() : command.attributes();
    if (command.owner() != null && !command.owner().isBlank()) {
      attrs = new java.util.HashMap<>(attrs);
      attrs.put("owner", command.owner());
    }
    String attrsJson;
    try {
      attrsJson = json.writeValueAsString(attrs);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize CI attributes", ex);
    }
    jdbc.update(
        """
        INSERT INTO configuration_item (id, name, class_key, status, attributes, created_at, updated_at)
        VALUES (?,?,?,?,?::jsonb,?,?)
        """,
        id,
        command.name().trim(),
        classKey,
        status.name(),
        attrsJson,
        Timestamp.from(now),
        Timestamp.from(now)
    );
    Map<String, Object> after = Map.of(
        "name", command.name().trim(),
        "classKey", classKey,
        "status", status.name()
    );
    audit.append(new AuditTrail.Entry(
        actor, "cmdb.ci-created", "configuration-item", id.toString(),
        Map.of(), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "cmdb.ci-created", 1, now, correlationId,
        "configuration-item", id.toString(), after
    ));
    return query.findById(id).orElseThrow(() -> new IllegalStateException("CI not readable after create"));
  }

  @Transactional
  public CiRelationship createRelationship(
      UUID sourceId,
      UUID targetId,
      CiRelationship.Type type,
      String actor
  ) {
    if (sourceId.equals(targetId)) {
      throw new IllegalArgumentException("Cannot relate a CI to itself");
    }
    if (query.findById(sourceId).isEmpty()) {
      throw new IllegalArgumentException("Source CI not found: " + sourceId);
    }
    if (query.findById(targetId).isEmpty()) {
      throw new IllegalArgumentException("Target CI not found: " + targetId);
    }
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    try {
      jdbc.update(
          """
          INSERT INTO ci_relationship (id, source_ci_id, target_ci_id, relationship_type)
          VALUES (?,?,?,?)
          """,
          id, sourceId, targetId, type.name()
      );
    } catch (org.springframework.dao.DuplicateKeyException ex) {
      throw new IllegalStateException("Relationship already exists");
    }
    Map<String, Object> after = Map.of(
        "sourceCiId", sourceId.toString(),
        "targetCiId", targetId.toString(),
        "type", type.name()
    );
    audit.append(new AuditTrail.Entry(
        actor, "cmdb.relationship-created", "configuration-item", sourceId.toString(),
        Map.of(), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "cmdb.relationship-created", 1, now, correlationId,
        "configuration-item", sourceId.toString(), after
    ));
    return new CiRelationship(id, sourceId, targetId, type);
  }

  @Transactional
  public void deleteRelationship(UUID relationshipId, String actor) {
    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    int n = jdbc.update("DELETE FROM ci_relationship WHERE id = ?", relationshipId);
    if (n == 0) {
      throw new IllegalArgumentException("Relationship not found: " + relationshipId);
    }
    Map<String, Object> after = Map.of("relationshipId", relationshipId.toString());
    audit.append(new AuditTrail.Entry(
        actor, "cmdb.relationship-deleted", "configuration-item", relationshipId.toString(),
        Map.of(), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "cmdb.relationship-deleted", 1, now, correlationId,
        "configuration-item", relationshipId.toString(), after
    ));
  }

  public record CreateCommand(
      String name,
      String classKey,
      ConfigurationItem.Status status,
      String owner,
      Map<String, Object> attributes
  ) {}
}
