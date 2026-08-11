package ru.ultimavox.itsm.platform.forms;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.forms.FormDefinition.Expression;
import ru.ultimavox.itsm.platform.forms.FormDefinition.Field;
import ru.ultimavox.itsm.platform.forms.FormDefinition.Section;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcFormDefinitionRepository implements FormDefinitionRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcFormDefinitionRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<FormDefinition> findActiveByObjectKey(String objectKey) {
        List<FormDefinition> rows = jdbc.query(
                """
                SELECT id, form_key, object_key, version, definition::text
                FROM form_definition
                WHERE org_id IN (?, 'default') AND object_key = ? AND active = true
                ORDER BY (org_id = ?) DESC, version DESC
                LIMIT 1
                """,
                (rs, i) -> map(
                        rs.getObject("id", UUID.class),
                        rs.getString("form_key"),
                        rs.getString("object_key"),
                        rs.getInt("version"),
                        rs.getString("definition")
                ),
                OrganizationContext.current(), objectKey, OrganizationContext.current()
        );
        return rows.stream().findFirst();
    }

    @Override
    public Optional<FormDefinition> findActiveByKey(String formKey) {
        List<FormDefinition> rows = jdbc.query(
                """
                SELECT id, form_key, object_key, version, definition::text
                FROM form_definition
                WHERE org_id IN (?, 'default') AND form_key = ? AND active = true
                ORDER BY (org_id = ?) DESC, version DESC
                LIMIT 1
                """,
                (rs, i) -> map(
                        rs.getObject("id", UUID.class),
                        rs.getString("form_key"),
                        rs.getString("object_key"),
                        rs.getInt("version"),
                        rs.getString("definition")
                ),
                OrganizationContext.current(), formKey, OrganizationContext.current()
        );
        return rows.stream().findFirst();
    }

    @Override
    public FormDefinition save(FormDefinition definition, String definitionJson) {
        UUID id = definition.id() != null ? definition.id() : UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO form_definition (id, org_id, form_key, object_key, version, active, definition)
                VALUES (?, ?, ?, ?, ?, true, ?::jsonb)
                """,
                id,
                OrganizationContext.current(),
                definition.key(),
                definition.objectKey(),
                definition.version(),
                definitionJson
        );
        return new FormDefinition(id, definition.key(), definition.objectKey(), definition.version(), definition.sections());
    }

    private FormDefinition map(UUID id, String formKey, String objectKey, int version, String definitionJson) {
        try {
            JsonNode root = json.readTree(definitionJson);
            List<Section> sections = new ArrayList<>();
            JsonNode sectionsNode = root.get("sections");
            if (sectionsNode != null && sectionsNode.isArray()) {
                for (JsonNode sectionNode : sectionsNode) {
                    List<Field> fields = new ArrayList<>();
                    JsonNode fieldsNode = sectionNode.get("fields");
                    if (fieldsNode != null && fieldsNode.isArray()) {
                        for (JsonNode fieldNode : fieldsNode) {
                            fields.add(new Field(
                                    fieldNode.path("attributeKey").asText(),
                                    fieldNode.path("required").asBoolean(false),
                                    parseExpression(fieldNode.get("visibleWhen")),
                                    parseExpression(fieldNode.get("readOnlyWhen"))
                            ));
                        }
                    }
                    sections.add(new Section(
                            sectionNode.path("key").asText(),
                            readStringMap(sectionNode.get("labels")),
                            fields
                    ));
                }
            }
            return new FormDefinition(id, formKey, objectKey, version, sections);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot parse form_definition for key=" + formKey, ex);
        }
    }

    private Expression parseExpression(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String language = node.path("language").asText(null);
        String source = node.path("source").asText(null);
        if (language == null || source == null) {
            return null;
        }
        return new Expression(language, source);
    }

    private Map<String, String> readStringMap(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        return json.convertValue(node, new TypeReference<>() {});
    }
}
