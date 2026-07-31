package ru.ultimavox.itsm.cmdb.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.cmdb.domain.CiRelationship;
import ru.ultimavox.itsm.cmdb.domain.ConfigurationItem;
import ru.ultimavox.itsm.cmdb.domain.ImpactGraph;

@Service
public class CmdbQuery {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public CmdbQuery(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<ConfigurationItem> search(String classKey, String status, String query) {
    String classFilter = blankToNull(classKey);
    String statusFilter = blankToNull(status);
    String q = blankToNull(query);
    return jdbc.query(
        """
            SELECT id, name, class_key, status, attributes::text AS attributes
            FROM configuration_item
            WHERE (? IS NULL OR class_key = ?)
              AND (? IS NULL OR status = ?)
              AND (? IS NULL OR name ILIKE '%' || ? || '%')
            ORDER BY name
            """,
        (rs, i) -> mapCi(
            (UUID) rs.getObject("id"),
            rs.getString("name"),
            rs.getString("class_key"),
            rs.getString("status"),
            rs.getString("attributes")
        ),
        classFilter, classFilter, statusFilter, statusFilter, q, q
    );
  }

  public Optional<ConfigurationItem> findById(UUID id) {
    List<ConfigurationItem> rows = jdbc.query(
        "SELECT id, name, class_key, status, attributes::text AS attributes FROM configuration_item WHERE id = ?",
        (rs, i) -> mapCi(
            (UUID) rs.getObject("id"),
            rs.getString("name"),
            rs.getString("class_key"),
            rs.getString("status"),
            rs.getString("attributes")
        ),
        id
    );
    return rows.stream().findFirst();
  }

  public List<CiRelationship> relationshipsFor(UUID ciId) {
    return jdbc.query(
        """
            SELECT id, source_ci_id, target_ci_id, relationship_type
            FROM ci_relationship
            WHERE source_ci_id = ? OR target_ci_id = ?
            ORDER BY relationship_type, id
            """,
        (rs, i) -> new CiRelationship(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("source_ci_id"),
            (UUID) rs.getObject("target_ci_id"),
            CiRelationship.Type.valueOf(rs.getString("relationship_type"))
        ),
        ciId, ciId
    );
  }

  public ImpactResult impactAnalysis(UUID ciId, int hops) {
    ConfigurationItem root = findById(ciId)
        .orElseThrow(() -> new IllegalArgumentException("Configuration item not found: " + ciId));
    int maxHops = hops <= 0 ? 1 : Math.min(hops, ImpactGraph.MAX_SUPPORTED_HOPS);

    List<ImpactGraph.Edge> edges = jdbc.query(
        "SELECT source_ci_id, target_ci_id, relationship_type FROM ci_relationship",
        (rs, i) -> new ImpactGraph.Edge(
            (UUID) rs.getObject("source_ci_id"),
            (UUID) rs.getObject("target_ci_id"),
            rs.getString("relationship_type")
        )
    );

    List<ImpactGraph.Node> nodes = ImpactGraph.traverse(ciId, maxHops, edges);
    List<ImpactedCi> impacted = nodes.stream()
        .map(node -> findById(node.ciId())
            .map(ci -> new ImpactedCi(ci.id(), ci.name(), ci.classKey(), ci.status().name(), node.hop(), node.viaRelationship()))
            .orElse(new ImpactedCi(node.ciId(), null, null, null, node.hop(), node.viaRelationship())))
        .toList();
    return new ImpactResult(root.id(), root.name(), maxHops, impacted);
  }

  private ConfigurationItem mapCi(UUID id, String name, String classKey, String status, String attributesJson) {
    Map<String, Object> attributes;
    try {
      attributes = json.readValue(attributesJson == null ? "{}" : attributesJson, new TypeReference<>() {});
    } catch (Exception ex) {
      attributes = Map.of();
    }
    return new ConfigurationItem(id, name, classKey, ConfigurationItem.Status.valueOf(status), attributes);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record ImpactedCi(
      UUID id,
      String name,
      String classKey,
      String status,
      int hop,
      String viaRelationship
  ) {}

  public record ImpactResult(UUID rootCiId, String rootName, int hops, List<ImpactedCi> impacted) {}
}
