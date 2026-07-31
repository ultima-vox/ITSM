package ru.ultimavox.itsm.platform.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.Transition;

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
                SELECT id, object_key, version, definition::text
                FROM workflow_definition
                WHERE object_key = ? AND active = true
                ORDER BY version DESC
                LIMIT 1
                """,
                (rs, i) -> map(
                        rs.getObject("id", UUID.class),
                        rs.getString("object_key"),
                        rs.getInt("version"),
                        rs.getString("definition")
                ),
                objectKey
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<WorkflowDefinitionView> listAll() {
        return jdbc.query(
                """
                SELECT id, object_key, version, active, definition::text
                FROM workflow_definition
                ORDER BY object_key, version DESC
                """,
                (rs, i) -> new WorkflowDefinitionView(
                        map(
                                rs.getObject("id", UUID.class),
                                rs.getString("object_key"),
                                rs.getInt("version"),
                                rs.getString("definition")
                        ),
                        rs.getBoolean("active")
                )
        );
    }

    private WorkflowDefinition map(UUID id, String objectKey, int version, String definitionJson) {
        try {
            JsonNode root = json.readTree(definitionJson);
            String initialState = root.path("initialState").asText(null);
            Set<String> states = new HashSet<>();
            JsonNode statesNode = root.get("states");
            if (statesNode != null && statesNode.isArray()) {
                statesNode.forEach(n -> states.add(n.asText()));
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
                            readStringSet(node.get("requiredFields"))
                    ));
                }
            }
            return new WorkflowDefinition(id, objectKey, version, initialState, states, transitions);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot parse workflow_definition for key=" + objectKey, ex);
        }
    }

    private Set<String> readStringSet(JsonNode node) {
        Set<String> result = new HashSet<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> result.add(n.asText()));
        }
        return result;
    }
}
