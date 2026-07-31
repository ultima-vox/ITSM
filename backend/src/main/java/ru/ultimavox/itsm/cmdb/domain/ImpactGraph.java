package ru.ultimavox.itsm.cmdb.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure BFS impact traversal over directed CI edges.
 * Hop 0 is the root CI; hops 1..N are related CIs reached by walking relationships.
 */
public final class ImpactGraph {
  private ImpactGraph() {}

  public record Edge(UUID from, UUID to, String relationshipType) {}

  public record Node(UUID ciId, int hop, String viaRelationship) {}

  /**
   * Returns impacted nodes excluding the root when hops &gt;= 1, including hop distance.
   * Edges are treated as bidirectional for blast-radius style impact analysis.
   */
  /** Hard ceiling to keep blast-radius queries bounded. */
  public static final int MAX_SUPPORTED_HOPS = 8;

  public static List<Node> traverse(UUID rootCiId, int maxHops, List<Edge> edges) {
    Objects.requireNonNull(rootCiId, "rootCiId");
    if (maxHops < 0 || maxHops > MAX_SUPPORTED_HOPS) {
      throw new IllegalArgumentException(
          "maxHops must be 0.." + MAX_SUPPORTED_HOPS
      );
    }
    Map<UUID, List<Edge>> adjacency = new HashMap<>();
    for (Edge edge : edges) {
      adjacency.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
      adjacency.computeIfAbsent(edge.to(), k -> new ArrayList<>())
          .add(new Edge(edge.to(), edge.from(), edge.relationshipType()));
    }

    List<Node> result = new ArrayList<>();
    Set<UUID> visited = new HashSet<>();
    ArrayDeque<Node> queue = new ArrayDeque<>();
    queue.add(new Node(rootCiId, 0, null));
    visited.add(rootCiId);

    while (!queue.isEmpty()) {
      Node current = queue.poll();
      if (current.hop() > 0) {
        result.add(current);
      }
      if (current.hop() >= maxHops) {
        continue;
      }
      for (Edge edge : adjacency.getOrDefault(current.ciId(), List.of())) {
        if (visited.add(edge.to())) {
          queue.add(new Node(edge.to(), current.hop() + 1, edge.relationshipType()));
        }
      }
    }
    return result;
  }
}
