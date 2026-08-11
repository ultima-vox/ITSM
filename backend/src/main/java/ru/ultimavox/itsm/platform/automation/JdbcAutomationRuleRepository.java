package ru.ultimavox.itsm.platform.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.automation.AutomationRule.Action;
import ru.ultimavox.itsm.platform.automation.AutomationRule.Condition;
import ru.ultimavox.itsm.platform.automation.AutomationRule.Operator;
import ru.ultimavox.itsm.platform.automation.AutomationRule.Trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcAutomationRuleRepository implements AutomationRuleRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcAutomationRuleRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<AutomationRule> findEnabledByEventType(String eventType) {
        return jdbc.query(
                """
                SELECT id, rule_key, version, enabled, definition
                FROM (
                  SELECT DISTINCT ON (rule_key) id, rule_key, version, enabled, definition::text AS definition
                  FROM automation_rule
                  WHERE org_id IN (?, 'default')
                    AND definition -> 'trigger' ->> 'eventType' = ?
                  ORDER BY rule_key, (org_id = ?) DESC
                ) scoped
                WHERE enabled = true
                """,
                (rs, i) -> map(
                        rs.getObject("id", UUID.class),
                        rs.getString("rule_key"),
                        rs.getInt("version"),
                        rs.getBoolean("enabled"),
                        rs.getString("definition")
                ),
                OrganizationContext.current(), eventType, OrganizationContext.current()
        );
    }

    @Override
    public List<AutomationRule> listAll() {
        return jdbc.query(
                """
                SELECT DISTINCT ON (rule_key) id, rule_key, version, enabled, definition::text
                FROM automation_rule
                WHERE org_id IN (?, 'default')
                ORDER BY rule_key, (org_id = ?) DESC
                """,
                (rs, i) -> map(
                        rs.getObject("id", UUID.class),
                        rs.getString("rule_key"),
                        rs.getInt("version"),
                        rs.getBoolean("enabled"),
                        rs.getString("definition")
                ), OrganizationContext.current(), OrganizationContext.current()
        );
    }

    @Override
    public Optional<AutomationRule> setEnabled(UUID id, boolean enabled) {
        String organization = OrganizationContext.current();
        List<AutomationRule> rows = jdbc.query(
                """
                WITH source AS (
                  SELECT rule_key, definition, version
                  FROM automation_rule
                  WHERE id = ? AND org_id IN (?, 'default')
                  ORDER BY (org_id = ?) DESC
                  LIMIT 1
                ), changed AS (
                  INSERT INTO automation_rule (org_id, rule_key, enabled, definition, version, updated_at)
                  SELECT ?, rule_key, ?, definition, version + 1, now() FROM source
                  ON CONFLICT (org_id, rule_key) DO UPDATE
                    SET enabled = EXCLUDED.enabled,
                        version = automation_rule.version + 1,
                        updated_at = now()
                  RETURNING id, rule_key, version, enabled, definition::text
                )
                SELECT id, rule_key, version, enabled, definition FROM changed
                """,
                (rs, i) -> map(
                        rs.getObject("id", UUID.class),
                        rs.getString("rule_key"),
                        rs.getInt("version"),
                        rs.getBoolean("enabled"),
                        rs.getString("definition")
                ),
                id, organization, organization, organization, enabled
        );
        return rows.stream().findFirst();
    }

    @Override
    public AutomationRule create(AutomationRule rule, String definitionJson) {
        return jdbc.query("""
                INSERT INTO automation_rule(id, org_id, rule_key, enabled, definition, version, updated_at)
                VALUES (?, ?, ?, ?, ?::jsonb, 1, now())
                RETURNING id, rule_key, version, enabled, definition::text
                """, mapper(), UUID.randomUUID(), OrganizationContext.current(), rule.ruleKey(),
                rule.enabled(), definitionJson).getFirst();
    }

    @Override
    public Optional<AutomationRule> update(UUID id, int expectedVersion, AutomationRule rule, String definitionJson) {
        String organization = OrganizationContext.current();
        List<AutomationRule> rows = jdbc.query("""
                WITH source AS (
                  SELECT rule_key FROM automation_rule WHERE id = ? AND org_id IN (?, 'default')
                  ORDER BY (org_id = ?) DESC LIMIT 1
                ), changed AS (
                  INSERT INTO automation_rule(org_id, rule_key, enabled, definition, version, updated_at)
                  SELECT ?, rule_key, ?, ?::jsonb, ? + 1, now() FROM source
                  ON CONFLICT (org_id, rule_key) DO UPDATE SET enabled = EXCLUDED.enabled,
                    definition = EXCLUDED.definition, version = automation_rule.version + 1, updated_at = now()
                  WHERE automation_rule.version = ?
                  RETURNING id, rule_key, version, enabled, definition::text
                ) SELECT * FROM changed
                """, mapper(), id, organization, organization, organization, rule.enabled(),
                definitionJson, expectedVersion, expectedVersion);
        return rows.stream().findFirst();
    }

    private org.springframework.jdbc.core.RowMapper<AutomationRule> mapper() {
        return (rs, i) -> map(rs.getObject("id", UUID.class), rs.getString("rule_key"),
                rs.getInt("version"), rs.getBoolean("enabled"), rs.getString("definition"));
    }

    private AutomationRule map(UUID id, String ruleKey, int version, boolean enabled, String definitionJson) {
        try {
            JsonNode root = json.readTree(definitionJson);
            String name = root.path("name").asText(ruleKey);
            String eventType = root.path("trigger").path("eventType").asText();

            List<Condition> conditions = new ArrayList<>();
            JsonNode conditionsNode = root.get("conditions");
            if (conditionsNode != null && conditionsNode.isArray()) {
                for (JsonNode node : conditionsNode) {
                    conditions.add(new Condition(
                            node.path("field").asText(),
                            Operator.valueOf(node.path("operator").asText()),
                            node.path("value").asText()
                    ));
                }
            }

            List<Action> actions = new ArrayList<>();
            JsonNode actionsNode = root.get("actions");
            if (actionsNode != null && actionsNode.isArray()) {
                for (JsonNode node : actionsNode) {
                    actions.add(new Action(
                            node.path("type").asText(),
                            readObjectMap(node.get("parameters"))
                    ));
                }
            }

            return new AutomationRule(id, ruleKey, name, version, enabled, new Trigger(eventType), conditions, actions);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot parse automation_rule key=" + ruleKey, ex);
        }
    }

    private Map<String, Object> readObjectMap(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> map = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (value.isTextual()) {
                map.put(entry.getKey(), value.asText());
            } else if (value.isNumber()) {
                map.put(entry.getKey(), value.numberValue());
            } else if (value.isBoolean()) {
                map.put(entry.getKey(), value.asBoolean());
            } else {
                map.put(entry.getKey(), value.toString());
            }
        }
        return map;
    }
}
