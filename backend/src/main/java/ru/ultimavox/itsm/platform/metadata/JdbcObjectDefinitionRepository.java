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
