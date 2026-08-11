package ru.ultimavox.itsm.platform.metadata;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

/** Creates immutable schema drafts and atomically publishes compatible tenant versions. */
@Service
public class ObjectDefinitionAdminService {
  private static final Set<String> REQUIRED_LOCALES = Set.of("ru", "en");
  private final ObjectDefinitionRepository repository;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public ObjectDefinitionAdminService(ObjectDefinitionRepository repository, AuditTrail audit,
                                      IntegrationEventOutbox outbox) {
    this.repository = repository;
    this.audit = audit;
    this.outbox = outbox;
  }

  @Transactional(readOnly = true)
  public List<ObjectDefinitionVersion> versions(String key) {
    validateKey(key, "object");
    return repository.findVersions(key);
  }

  @Transactional
  public ObjectDefinition createDraft(String actor, Draft command) {
    ObjectDefinition validated = validate(command);
    ObjectDefinition saved = repository.insertNextDraft(validated);
    record(actor, "metadata.object-draft-created", saved, Map.of(),
        Map.of("version", saved.version(), "attributes", saved.attributes().size()));
    return saved;
  }

  @Transactional
  public ObjectDefinition publish(String actor, String key, int version) {
    if (version < 1) throw new IllegalArgumentException("version must be positive");
    ObjectDefinitionVersion targetVersion = repository.findVersions(key).stream()
        .filter(candidate -> candidate.definition().version() == version)
        .findFirst()
        .orElseThrow(() -> new ObjectDefinitionNotFoundException(key + " v" + version));
    ObjectDefinition target = targetVersion.definition();
    if (targetVersion.active()) return target;
    validateDefinition(target);
    repository.findActiveByKey(key).ifPresent(active -> validateCompatible(active, target));
    ObjectDefinition published = repository.publish(key, version);
    record(actor, "metadata.object-published", published, Map.of(), Map.of("version", version));
    return published;
  }

  private ObjectDefinition validate(Draft command) {
    if (command == null) throw new IllegalArgumentException("definition body is required");
    validateKey(command.key(), "object");
    if (command.attributes() == null || command.attributes().isEmpty()) {
      throw new IllegalArgumentException("At least one attribute is required");
    }
    if (command.attributes().size() > 500) throw new IllegalArgumentException("At most 500 attributes are allowed");
    Map<String, AttributeDefinition> attributes = new LinkedHashMap<>();
    for (AttributeDefinition attribute : command.attributes()) {
      validateKey(attribute.key(), "attribute");
      validateLabels(attribute.labels(), "attribute " + attribute.key());
      if (attribute.enumValues().size() > 500
          || attribute.enumValues().stream().anyMatch(value -> value == null || value.isBlank())
          || new HashSet<>(attribute.enumValues()).size() != attribute.enumValues().size()) {
        throw new IllegalArgumentException("Invalid enum values for attribute: " + attribute.key());
      }
      if (attributes.putIfAbsent(attribute.key(), attribute) != null) {
        throw new IllegalArgumentException("Duplicate attribute key: " + attribute.key());
      }
    }
    List<RelationDefinition> relations = command.relations() == null ? List.of() : List.copyOf(command.relations());
    if (relations.size() > 100) throw new IllegalArgumentException("At most 100 relations are allowed");
    Set<String> relationKeys = new HashSet<>();
    for (RelationDefinition relation : relations) {
      validateKey(relation.key(), "relation");
      validateKey(relation.targetObjectKey(), "relation target");
      validateLabels(relation.labels(), "relation " + relation.key());
      if (!relationKeys.add(relation.key())) throw new IllegalArgumentException("Duplicate relation key: " + relation.key());
      if (!relation.targetObjectKey().equals(command.key())
          && repository.findActiveByKey(relation.targetObjectKey()).isEmpty()) {
        throw new IllegalArgumentException("Unknown active relation target: " + relation.targetObjectKey());
      }
    }
    validateLabels(command.labels(), "object " + command.key());
    ObjectDefinition definition = new ObjectDefinition(UUID.randomUUID(), command.key(), 1,
        command.labels(), attributes, relations);
    validateDefinition(definition);
    return definition;
  }

  private void validateDefinition(ObjectDefinition definition) {
    validateKey(definition.key(), "object");
    validateLabels(definition.labels(), "object " + definition.key());
    definition.attributeList().forEach(attribute -> {
      validateKey(attribute.key(), "attribute");
      validateLabels(attribute.labels(), "attribute " + attribute.key());
    });
  }

  private void validateCompatible(ObjectDefinition active, ObjectDefinition target) {
    for (AttributeDefinition oldAttribute : active.attributeList()) {
      AttributeDefinition next = target.attributes().get(oldAttribute.key());
      if (next == null) throw new IllegalArgumentException("Published attribute cannot be removed: " + oldAttribute.key());
      if (next.type() != oldAttribute.type()) {
        throw new IllegalArgumentException("Published attribute type cannot change: " + oldAttribute.key());
      }
      if (!oldAttribute.required() && next.required()) {
        throw new IllegalArgumentException("Optional published attribute cannot become required: " + oldAttribute.key());
      }
      if (oldAttribute.type() == AttributeDefinition.AttributeType.ENUM
          && !next.enumValues().containsAll(oldAttribute.enumValues())) {
        throw new IllegalArgumentException("Published enum values cannot be removed: " + oldAttribute.key());
      }
    }
    Map<String, RelationDefinition> nextRelations = target.relations().stream()
        .collect(java.util.stream.Collectors.toMap(RelationDefinition::key, relation -> relation));
    for (RelationDefinition oldRelation : active.relations()) {
      RelationDefinition next = nextRelations.get(oldRelation.key());
      if (next == null) {
        throw new IllegalArgumentException("Published relation cannot be removed: " + oldRelation.key());
      }
      if (!next.targetObjectKey().equals(oldRelation.targetObjectKey())
          || next.cardinality() != oldRelation.cardinality()) {
        throw new IllegalArgumentException("Published relation shape cannot change: " + oldRelation.key());
      }
      if (!oldRelation.required() && next.required()) {
        throw new IllegalArgumentException("Optional published relation cannot become required: " + oldRelation.key());
      }
    }
  }

  private void validateLabels(Map<String, String> labels, String owner) {
    if (labels == null || !labels.keySet().containsAll(REQUIRED_LOCALES)
        || REQUIRED_LOCALES.stream().anyMatch(locale -> labels.get(locale) == null || labels.get(locale).isBlank())) {
      throw new IllegalArgumentException(owner + " requires non-blank ru and en labels");
    }
    if (labels.size() > 20 || labels.values().stream().anyMatch(value -> value.length() > 240)) {
      throw new IllegalArgumentException(owner + " labels exceed limits");
    }
  }

  private void validateKey(String key, String kind) {
    if (key == null || !key.matches("[a-z][a-z0-9-]{0,99}")) {
      throw new IllegalArgumentException("Invalid " + kind + " key: " + key);
    }
  }

  private void record(String actor, String action, ObjectDefinition definition,
                      Map<String, Object> before, Map<String, Object> after) {
    Instant now = Instant.now();
    UUID correlation = UUID.randomUUID();
    audit.append(new AuditTrail.Entry(actor, action, "object_definition", definition.key(),
        before, after, correlation, now));
    outbox.record(new DomainEvent(UUID.randomUUID(), action, 1, now, correlation,
        "object_definition", definition.key(), after));
  }

  public record Draft(String key, Map<String, String> labels,
                      List<AttributeDefinition> attributes, List<RelationDefinition> relations) {}
}
