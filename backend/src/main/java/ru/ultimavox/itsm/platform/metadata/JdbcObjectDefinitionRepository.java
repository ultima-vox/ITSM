package ru.ultimavox.itsm.platform.metadata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.metadata.AttributeDefinition.AttributeType;
import ru.ultimavox.itsm.platform.metadata.RelationDefinition.Cardinality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcObjectDefinitionRepository implements ObjectDefinitionRepository {

    private static final String SELECT_BASE = """
            SELECT id, object_key, version, definition::text
            FROM object_definition
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcObjectDefinitionRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<ObjectDefinition> findActiveByKey(String objectKey) {
        List<ObjectDefinition> rows = jdbc.query(
                SELECT_BASE + " WHERE org_id IN (?, 'default') AND object_key = ? AND active = true "
                        + "ORDER BY (org_id = ?) DESC, version DESC LIMIT 1",
                (rs, i) -> map(rs.getObject("id", UUID.class), rs.getString("object_key"),
                        rs.getInt("version"), rs.getString("definition")),
                OrganizationContext.current(), objectKey, OrganizationContext.current()
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<ObjectDefinition> findAllActive() {
        return jdbc.query(
                """
                SELECT DISTINCT ON (object_key) id, object_key, version, definition::text
                FROM object_definition
                WHERE org_id IN (?, 'default') AND active = true
                ORDER BY object_key, (org_id = ?) DESC, version DESC
                """,
                (rs, i) -> map(rs.getObject("id", UUID.class), rs.getString("object_key"),
                        rs.getInt("version"), rs.getString("definition")),
                OrganizationContext.current(), OrganizationContext.current()
        );
    }

    @Override
    public Optional<ObjectDefinition> findByKeyAndVersion(String objectKey, int version) {
        List<ObjectDefinition> rows = jdbc.query(
                SELECT_BASE + " WHERE org_id IN (?, 'default') AND object_key = ? AND version = ? "
                        + "ORDER BY (org_id = ?) DESC LIMIT 1",
                (rs, i) -> map(rs.getObject("id", UUID.class), rs.getString("object_key"),
                        rs.getInt("version"), rs.getString("definition")),
                OrganizationContext.current(), objectKey, version, OrganizationContext.current()
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<ObjectDefinitionVersion> findVersions(String objectKey) {
        return jdbc.query(
                """
                SELECT id,object_key,version,active,definition::text
                FROM object_definition WHERE org_id=? AND object_key=? ORDER BY version DESC
                """,
                (rs, i) -> new ObjectDefinitionVersion(
                        map(rs.getObject("id", UUID.class), rs.getString("object_key"),
                                rs.getInt("version"), rs.getString("definition")),
                        rs.getBoolean("active")),
                OrganizationContext.current(), objectKey);
    }

    @Override
    @Transactional
    public ObjectDefinition insertNextDraft(ObjectDefinition draft) {
        String org = OrganizationContext.current();
        lockDefinition(org, draft.key());
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version),0)+1 FROM object_definition WHERE org_id=? AND object_key=?",
                Integer.class, org, draft.key());
        int version = next == null ? 1 : next;
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO object_definition(id,org_id,object_key,version,active,definition)
                VALUES (?,?,?,?,false,?::jsonb)
                """, id, org, draft.key(), version, writeDefinition(draft));
        return new ObjectDefinition(id, draft.key(), version, draft.labels(), draft.attributes(), draft.relations());
    }

    @Override
    @Transactional
    public ObjectDefinition publish(String objectKey, int version) {
        String org = OrganizationContext.current();
        lockDefinition(org, objectKey);
        ObjectDefinition target = findTenantVersion(objectKey, version)
                .orElseThrow(() -> new ObjectDefinitionNotFoundException(objectKey + " v" + version));
        jdbc.update("UPDATE object_definition SET active=false WHERE org_id=? AND object_key=?",
                org, objectKey);
        int changed = jdbc.update("""
                UPDATE object_definition SET active=true
                WHERE org_id=? AND object_key=? AND version=?
                """, org, objectKey, version);
        if (changed != 1) throw new IllegalStateException("Object definition publication conflict");
        return target;
    }

    private Optional<ObjectDefinition> findTenantVersion(String objectKey, int version) {
        return jdbc.query(
                SELECT_BASE + " WHERE org_id=? AND object_key=? AND version=? LIMIT 1",
                (rs, i) -> map(rs.getObject("id", UUID.class), rs.getString("object_key"),
                        rs.getInt("version"), rs.getString("definition")),
                OrganizationContext.current(), objectKey, version).stream().findFirst();
    }

    private String writeDefinition(ObjectDefinition definition) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("labels", definition.labels());
        body.put("attributes", definition.attributeList());
        body.put("relations", definition.relations());
        try {
            return json.writeValueAsString(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize object definition", ex);
        }
    }

    private void lockDefinition(String org, String objectKey) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                ps -> ps.setString(1, org + ":object-definition:" + objectKey),
                rs -> null);
    }

    private ObjectDefinition map(UUID id, String key, int version, String definitionJson) {
        try {
            JsonNode root = json.readTree(definitionJson);
            Map<String, String> labels = readStringMap(root.get("labels"));

            Map<String, AttributeDefinition> attributes = new LinkedHashMap<>();
            JsonNode attrsNode = root.get("attributes");
            if (attrsNode != null && attrsNode.isArray()) {
                for (JsonNode node : attrsNode) {
                    AttributeDefinition attr = parseAttribute(node);
                    attributes.put(attr.key(), attr);
                }
            }

            // Legacy shape: { "fields": { "title": { "type": "TEXT", ... } } }
            JsonNode fieldsNode = root.get("fields");
            if (attributes.isEmpty() && fieldsNode != null && fieldsNode.isObject()) {
                fieldsNode.fields().forEachRemaining(entry -> {
                    AttributeDefinition attr = parseAttributeWithKey(entry.getKey(), entry.getValue());
                    attributes.put(attr.key(), attr);
                });
            }

            List<RelationDefinition> relations = new ArrayList<>();
            JsonNode relsNode = root.get("relations");
            if (relsNode != null && relsNode.isArray()) {
                for (JsonNode node : relsNode) {
                    relations.add(parseRelation(node));
                }
            }

            return new ObjectDefinition(id, key, version, labels, attributes, relations);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot parse object_definition for key=" + key, ex);
        }
    }

    private AttributeDefinition parseAttribute(JsonNode node) {
        return parseAttributeWithKey(text(node, "key"), node);
    }

    private AttributeDefinition parseAttributeWithKey(String key, JsonNode node) {
        AttributeType type = AttributeType.valueOf(text(node, "type"));
        boolean required = node.path("required").asBoolean(false);
        boolean searchable = node.path("searchable").asBoolean(false);
        Map<String, String> labels = readStringMap(node.get("labels"));
        List<String> enumValues = new ArrayList<>();
        JsonNode enums = node.get("enumValues");
        if (enums != null && enums.isArray()) {
            enums.forEach(e -> enumValues.add(e.asText()));
        }
        return new AttributeDefinition(key, type, required, searchable, labels, enumValues);
    }

    private RelationDefinition parseRelation(JsonNode node) {
        return new RelationDefinition(
                text(node, "key"),
                text(node, "targetObjectKey"),
                Cardinality.valueOf(text(node, "cardinality")),
                node.path("required").asBoolean(false),
                readStringMap(node.get("labels"))
        );
    }

    private Map<String, String> readStringMap(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        return json.convertValue(node, new TypeReference<>() {});
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing required field '" + field + "' in definition JSON");
        }
        return value.asText();
    }
}
