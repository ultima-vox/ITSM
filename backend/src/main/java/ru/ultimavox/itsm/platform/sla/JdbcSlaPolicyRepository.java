package ru.ultimavox.itsm.platform.sla;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
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
                SELECT id, policy_key, definition::text
                FROM sla_policy
                WHERE policy_key = ? AND enabled = true
                """,
                (rs, i) -> map(
                        rs.getObject("id", UUID.class),
                        rs.getString("policy_key"),
                        rs.getString("definition")
                ),
                policyKey
        );
        return rows.stream().findFirst();
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
