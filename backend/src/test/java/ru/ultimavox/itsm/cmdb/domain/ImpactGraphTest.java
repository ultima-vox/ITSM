package ru.ultimavox.itsm.cmdb.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImpactGraphTest {
  private static final UUID SERVICE = UUID.fromString("c1000000-0000-4000-8000-000000000001");
  private static final UUID APP = UUID.fromString("c1000000-0000-4000-8000-000000000002");
  private static final UUID DB = UUID.fromString("c1000000-0000-4000-8000-000000000003");
  private static final UUID HOST = UUID.fromString("c1000000-0000-4000-8000-000000000004");

  private final List<ImpactGraph.Edge> seedEdges = List.of(
      new ImpactGraph.Edge(SERVICE, APP, "DEPENDS_ON"),
      new ImpactGraph.Edge(APP, DB, "DEPENDS_ON"),
      new ImpactGraph.Edge(DB, HOST, "HOSTED_ON"),
      new ImpactGraph.Edge(APP, HOST, "RUNS_ON")
  );

  @Test
  void one_hop_returns_direct_neighbors_only() {
    var impacted = ImpactGraph.traverse(SERVICE, 1, seedEdges);

    assertThat(impacted).extracting(ImpactGraph.Node::ciId).containsExactly(APP);
    assertThat(impacted.getFirst().hop()).isEqualTo(1);
  }

  @Test
  void two_hops_reach_transitive_dependencies() {
    var impacted = ImpactGraph.traverse(SERVICE, 2, seedEdges);

    assertThat(impacted).extracting(ImpactGraph.Node::ciId)
        .contains(APP, DB, HOST);
    assertThat(impacted).allMatch(node -> node.hop() >= 1 && node.hop() <= 2);
    assertThat(impacted).noneMatch(node -> node.ciId().equals(SERVICE));
  }

  @Test
  void multi_hop_beyond_legacy_stub_limit() {
    var impacted = ImpactGraph.traverse(SERVICE, 4, seedEdges);
    assertThat(impacted).extracting(ImpactGraph.Node::ciId)
        .contains(APP, DB, HOST);
  }

  @Test
  void rejects_hops_outside_supported_range() {
    assertThatThrownBy(() -> ImpactGraph.traverse(SERVICE, ImpactGraph.MAX_SUPPORTED_HOPS + 1, seedEdges))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxHops");
    assertThatThrownBy(() -> ImpactGraph.traverse(SERVICE, -1, seedEdges))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
