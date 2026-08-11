package ru.ultimavox.itsm.platform.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.Transition;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.ApprovalMode;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.ApprovalRequirement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class JdbcWorkflowDefinitionRepository implements WorkflowDefinitionRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcWorkflowDefinitionRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<WorkflowDefinition> findActiveByObjectKey(String objectKey) {
        List<WorkflowDefinition> rows = jdbc.query(
                """
                SELECT id, object_key, version, definition
                FROM (
                  SELECT DISTINCT ON (object_key, version)
                         id, object_key, version, active, definition::text AS definition, org_id
                  FROM workflow_definition
                  WHERE org_id IN (?, 'default') AND object_key = ?
                  ORDER BY object_key, version, (org_id = ?) DESC
                ) scoped
                WHERE active = true
                ORDER BY (org_id = ?) DESC, version DESC
                LIMIT 1
                """,
                (rs, i) -> map(
                        rs.getObject("id", UUID.class),
                        rs.getString("object_key"),
                        rs.getInt("version"),
                        rs.getString("definition")
                ),
                OrganizationContext.current(), objectKey,
                OrganizationContext.current(), OrganizationContext.current()
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<WorkflowDefinitionView> listAll() {
        return jdbc.query(
                """
                SELECT DISTINCT ON (object_key, version) id, object_key, version, active, definition::text
                FROM workflow_definition
                WHERE org_id IN (?, 'default')
                ORDER BY object_key, version DESC, (org_id = ?) DESC
                """,
                (rs, i) -> new WorkflowDefinitionView(
                        map(
                                rs.getObject("id", UUID.class),
                                rs.getString("object_key"),
                                rs.getInt("version"),
                                rs.getString("definition")
                        ),
                        rs.getBoolean("active")
                ), OrganizationContext.current(), OrganizationContext.current()
        );
    }

    @Override
    public Optional<WorkflowDefinition> findByObjectKeyAndVersion(String objectKey, int version) {
        List<WorkflowDefinition> rows = jdbc.query(
                """
                SELECT id, object_key, version, definition::text
                FROM workflow_definition
                WHERE org_id IN (?, 'default') AND object_key=? AND version=?
                ORDER BY (org_id = ?) DESC
                LIMIT 1
                """,
                (rs, i) -> map(rs.getObject("id", UUID.class), rs.getString("object_key"),
                        rs.getInt("version"), rs.getString("definition")),
                OrganizationContext.current(), objectKey, version, OrganizationContext.current()
        );
        return rows.stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<WorkflowDefinitionView> setActive(UUID id, boolean active) {
        String organization = OrganizationContext.current();
        List<WorkflowDefinitionView> rows = jdbc.query(
                """
                WITH source AS (
                  SELECT object_key, version, definition
                  FROM workflow_definition
                  WHERE id = ? AND org_id IN (?, 'default')
                  ORDER BY (org_id = ?) DESC
                  LIMIT 1
                ), deactivated AS (
                  UPDATE workflow_definition
                  SET active = false
                  WHERE org_id = ? AND object_key = (SELECT object_key FROM source) AND ? = true
                  RETURNING id
                ), changed AS (
                  INSERT INTO workflow_definition (org_id, object_key, version, active, definition)
                  SELECT ?, object_key, version, ?, definition FROM source
                  ON CONFLICT (org_id, object_key, version) DO UPDATE
                    SET active = EXCLUDED.active
                  RETURNING id, object_key, version, active, definition::text
                )
                SELECT id, object_key, version, active, definition FROM changed
                """,
                (rs, i) -> new WorkflowDefinitionView(
                        map(rs.getObject("id", UUID.class), rs.getString("object_key"),
                                rs.getInt("version"), rs.getString("definition")),
                        rs.getBoolean("active")
                ),
                id, organization, organization,
                organization, active,
                organization, active
        );
        return rows.stream().findFirst();
    }

    private WorkflowDefinition map(UUID id, String objectKey, int version, String definitionJson) {
        try {
            JsonNode root = json.readTree(definitionJson);
            // Seeds use either "initialState" (work-item) or "initial" (catalog-request).
            String initialState = textOrNull(root, "initialState");
            if (initialState == null) {
                initialState = textOrNull(root, "initial");
            }
            Set<String> states = new HashSet<>();
            JsonNode statesNode = root.get("states");
            if (statesNode != null && statesNode.isArray()) {
                statesNode.forEach(n -> states.add(n.asText()));
            }
            if (initialState == null && !states.isEmpty()) {
                initialState = states.iterator().next();
            }
            if (initialState == null) {
                initialState = "NEW";
            }
            if (!states.contains(initialState)) {
                states.add(initialState);
            }

            List<Transition> transitions = new ArrayList<>();
            JsonNode transitionsNode = root.get("transitions");
            if (transitionsNode != null && transitionsNode.isArray()) {
                for (JsonNode node : transitionsNode) {
                    transitions.add(new Transition(
                            node.path("key").asText(),
                            node.path("from").asText(),
                            node.path("to").asText(),
                            readStringSet(node.get("requiredPermissions")),
                            readStringSet(node.get("requiredFields")),
                            readApproval(node.get("approval"))
                    ));
                }
            }
            return new WorkflowDefinition(id, objectKey, version, initialState, states, transitions);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot parse workflow_definition for key=" + objectKey, ex);
        }
    }

    private static String textOrNull(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        String v = n.asText(null);
        return v == null || v.isBlank() ? null : v;
    }

    private Set<String> readStringSet(JsonNode node) {
        Set<String> result = new HashSet<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> result.add(n.asText()));
        }
        return result;
    }

    private ApprovalRequirement readApproval(JsonNode node) {
        if (node == null || node.isNull()) return null;
        ApprovalMode mode = ApprovalMode.valueOf(node.path("mode").asText());
        Integer quorum = node.hasNonNull("quorum") ? node.get("quorum").asInt() : null;
        return new ApprovalRequirement(mode, readStringSet(node.get("voterRoles")), quorum);
    }
}
