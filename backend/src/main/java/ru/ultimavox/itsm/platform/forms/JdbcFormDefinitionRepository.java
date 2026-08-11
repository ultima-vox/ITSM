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

    @Override
    public List<FormDefinitionVersion> findVersions(String formKey) {
        return jdbc.query("""
                SELECT id, form_key, object_key, version, active, definition::text
                FROM form_definition WHERE org_id = ? AND form_key = ? ORDER BY version DESC
                """, (rs, i) -> new FormDefinitionVersion(map(rs.getObject("id", UUID.class),
                rs.getString("form_key"), rs.getString("object_key"), rs.getInt("version"),
                rs.getString("definition")), rs.getBoolean("active")),
                OrganizationContext.current(), formKey);
    }

    @Override
    public FormDefinition insertNextDraft(FormDefinition definition, String definitionJson) {
        String org = OrganizationContext.current();
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                ps -> ps.setString(1, org + ":form:" + definition.key()), rs -> null);
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version), 0) + 1 FROM form_definition
                WHERE org_id = ? AND form_key = ?
                """, Integer.class, org, definition.key());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO form_definition(id, org_id, form_key, object_key, version, active, definition)
                VALUES (?, ?, ?, ?, ?, false, ?::jsonb)
                """, id, org, definition.key(), definition.objectKey(), next, definitionJson);
        return new FormDefinition(id, definition.key(), definition.objectKey(), next, definition.sections());
    }

    @Override
    public FormDefinition publish(String formKey, int version) {
        String org = OrganizationContext.current();
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                ps -> ps.setString(1, org + ":form:" + formKey), rs -> null);
        FormDefinition target = findVersions(formKey).stream()
                .filter(item -> item.definition().version() == version)
                .map(FormDefinitionVersion::definition).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown form version: " + formKey + " v" + version));
        jdbc.update("UPDATE form_definition SET active = false WHERE org_id = ? AND form_key = ?", org, formKey);
        int changed = jdbc.update("""
                UPDATE form_definition SET active = true
                WHERE org_id = ? AND form_key = ? AND version = ?
                """, org, formKey, version);
        if (changed != 1) throw new IllegalStateException("Form publication lost target version");
        return target;
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
