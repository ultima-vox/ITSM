package ru.ultimavox.itsm.platform.sla;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.sla.SlaPolicy.Target;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class JdbcSlaPolicyRepository implements SlaPolicyRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcSlaPolicyRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<SlaPolicy> findByKey(String policyKey) {
        List<SlaPolicy> rows = jdbc.query(
                """
                SELECT id, policy_key, definition
                FROM (
                  SELECT id, policy_key, enabled, definition::text AS definition
                  FROM sla_policy
                  WHERE org_id IN (?, 'default') AND policy_key = ?
                  ORDER BY (org_id = ?) DESC
                  LIMIT 1
                ) scoped
                WHERE enabled = true
                """,
                (rs, i) -> map(
                        rs.getObject("id", UUID.class),
                        rs.getString("policy_key"),
                        rs.getString("definition")
                ),
                OrganizationContext.current(), policyKey, OrganizationContext.current()
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<SlaPolicyView> listAll() {
        return jdbc.query(
                """
                SELECT DISTINCT ON (policy_key) id, policy_key, enabled, version, definition::text
                FROM sla_policy
                WHERE org_id IN (?, 'default')
                ORDER BY policy_key, (org_id = ?) DESC
                """,
                (rs, i) -> new SlaPolicyView(
                        map(
                                rs.getObject("id", UUID.class),
                                rs.getString("policy_key"),
                                rs.getString("definition")
                        ),
                        rs.getBoolean("enabled"),
                        rs.getInt("version")
                ), OrganizationContext.current(), OrganizationContext.current()
        );
    }

    @Override
    @Transactional
    public Optional<SlaPolicyView> update(UUID id, int expectedVersion, Boolean enabled, List<Target> targets) {
        String organization = OrganizationContext.current();
        List<PolicySource> sources = jdbc.query(
                """
                SELECT policy_key, enabled, version, definition::text
                FROM sla_policy
                WHERE id = ? AND org_id IN (?, 'default') AND version = ?
                ORDER BY (org_id = ?) DESC
                LIMIT 1
                """,
                (rs, i) -> new PolicySource(
                        rs.getString("policy_key"), rs.getBoolean("enabled"),
                        rs.getInt("version"), rs.getString("definition")
                ),
                id, organization, expectedVersion, organization
        );
        if (sources.isEmpty()) {
            return Optional.empty();
        }
        PolicySource source = sources.getFirst();
        try {
            ObjectNode definition = (ObjectNode) json.readTree(source.definition());
            if (targets != null) {
                ArrayNode array = definition.putArray("targets");
                for (Target target : targets) {
                    ObjectNode node = array.addObject();
                    node.put("metric", target.metric());
                    node.put("condition", target.condition());
                    node.put("targetMinutes", target.target() == null ? 0 : target.target().toMinutes());
                    node.put("warningBeforeMinutes",
                            target.warningBefore() == null ? 0 : target.warningBefore().toMinutes());
                }
            }
            boolean nextEnabled = enabled == null ? source.enabled() : enabled;
            String raw = json.writeValueAsString(definition);
            List<SlaPolicyView> rows = jdbc.query(
                    """
                    INSERT INTO sla_policy (org_id, policy_key, enabled, definition, version, updated_at)
                    VALUES (?, ?, ?, ?::jsonb, ? + 1, now())
                    ON CONFLICT (org_id, policy_key) DO UPDATE
                      SET enabled = EXCLUDED.enabled,
                          definition = EXCLUDED.definition,
                          version = sla_policy.version + 1,
                          updated_at = now()
                      WHERE sla_policy.version = ?
                    RETURNING id, policy_key, enabled, version, definition::text
                    """,
                    (rs, i) -> new SlaPolicyView(
                            map(rs.getObject("id", UUID.class), rs.getString("policy_key"),
                                    rs.getString("definition")),
                            rs.getBoolean("enabled"), rs.getInt("version")
                    ),
                    organization, source.key(), nextEnabled, raw, expectedVersion, expectedVersion
            );
            return rows.stream().findFirst();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot update sla_policy id=" + id, ex);
        }
    }

    private record PolicySource(String key, boolean enabled, int version, String definition) {}

    @Override
    @Transactional
    public SlaPolicyView create(String policyKey, String calendarKey, List<Target> targets, Set<String> pauseStates) {
        String organization = OrganizationContext.current();
        try {
            ObjectNode definition = json.createObjectNode();
            definition.put("calendarKey", calendarKey == null ? "default-business" : calendarKey);
            ArrayNode array = definition.putArray("targets");
            if (targets != null) {
                for (Target target : targets) {
                    ObjectNode node = array.addObject();
                    node.put("metric", target.metric());
                    node.put("condition", target.condition() == null ? "" : target.condition());
                    node.put("targetMinutes", target.target() == null ? 0 : target.target().toMinutes());
                    node.put("warningBeforeMinutes",
                            target.warningBefore() == null ? 0 : target.warningBefore().toMinutes());
                }
            }
            ArrayNode pauseArray = definition.putArray("pauseStates");
            if (pauseStates != null) {
                pauseStates.forEach(pauseArray::add);
            }
            String raw = json.writeValueAsString(definition);
            List<SlaPolicyView> rows = jdbc.query(
                    """
                    INSERT INTO sla_policy (org_id, policy_key, enabled, definition, version, updated_at)
                    VALUES (?, ?, true, ?::jsonb, 1, now())
                    ON CONFLICT (org_id, policy_key) DO NOTHING
                    RETURNING id, policy_key, enabled, version, definition::text
                    """,
                    (rs, i) -> new SlaPolicyView(
                            map(rs.getObject("id", UUID.class), rs.getString("policy_key"),
                                    rs.getString("definition")),
                            rs.getBoolean("enabled"), rs.getInt("version")
                    ),
                    organization, policyKey, raw
            );
            if (rows.isEmpty()) {
                throw new IllegalStateException("Policy '" + policyKey + "' already exists for this organization");
            }
            return rows.getFirst();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create sla_policy key=" + policyKey, ex);
        }
    }

    @Override
    @Transactional
    public boolean delete(UUID id) {
        int n = jdbc.update(
                "DELETE FROM sla_policy WHERE id = ? AND org_id = ?",
                id, OrganizationContext.current()
        );
        return n > 0;
    }

    private SlaPolicy map(UUID id, String key, String definitionJson) {
        try {
            JsonNode root = json.readTree(definitionJson);
            String calendarKey = root.path("calendarKey").asText("default-business");

            List<Target> targets = new ArrayList<>();
            JsonNode targetsNode = root.get("targets");
            if (targetsNode != null && targetsNode.isArray()) {
                for (JsonNode node : targetsNode) {
                    long targetMinutes = node.path("targetMinutes").asLong(0);
                    long warningMinutes = node.path("warningBeforeMinutes").asLong(0);
                    targets.add(new Target(
                            node.path("metric").asText(),
                            node.path("condition").asText(""),
                            Duration.ofMinutes(targetMinutes),
                            Duration.ofMinutes(warningMinutes)
                    ));
                }
            }

            Set<String> pauseStates = new HashSet<>();
            JsonNode pauseNode = root.get("pauseStates");
            if (pauseNode != null && pauseNode.isArray()) {
                pauseNode.forEach(n -> pauseStates.add(n.asText()));
            }

            return new SlaPolicy(id, key, calendarKey, targets, pauseStates);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot parse sla_policy key=" + key, ex);
        }
    }
}
