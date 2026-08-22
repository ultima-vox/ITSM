package ru.ultimavox.itsm.releasemanagement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReleaseTest {
  private Release release(Release.Status status, String deploymentPlan, String rollbackPlan,
                          String testSummary, Release.GoDecision decision) {
    return new Release(
        UUID.randomUUID(),
        "REL-001000",
        "Payments 4.2",
        Release.Type.MINOR,
        status,
        "Quarterly payments release",
        deploymentPlan,
        rollbackPlan,
        testSummary,
        decision,
        null,
        decision == null ? null : "carol",
        decision == null ? null : Instant.parse("2026-08-20T10:00:00Z"),
        "carol",
        Instant.parse("2026-08-21T20:00:00Z"),
        Instant.parse("2026-08-21T23:00:00Z"),
        null,
        null,
        0
    );
  }

  @Test
  void planning_moves_to_build() {
    Release planning = release(Release.Status.PLANNING, null, null, null, null);
    assertThat(planning.transition(Release.Status.BUILD).status()).isEqualTo(Release.Status.BUILD);
  }

  @Test
  void planning_cannot_skip_to_deploying() {
    Release planning = release(Release.Status.PLANNING, "deploy", "rollback", "tested", Release.GoDecision.GO);
    assertThatThrownBy(() -> planning.transition(Release.Status.DEPLOYING))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not allowed");
  }

  @Test
  void testing_requires_a_deployment_and_rollback_plan() {
    Release build = release(Release.Status.BUILD, "deploy", null, null, null);
    assertThatThrownBy(() -> build.transition(Release.Status.TESTING))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("rollback plan");
  }

  @Test
  void go_no_go_review_requires_a_test_summary() {
    Release testing = release(Release.Status.TESTING, "deploy", "rollback", "  ", null);
    assertThatThrownBy(() -> testing.transition(Release.Status.GO_NO_GO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("test summary");
  }

  @Test
  void deployment_requires_a_recorded_go_decision() {
    Release review = release(Release.Status.GO_NO_GO, "deploy", "rollback", "tested", null);
    assertThatThrownBy(() -> review.transition(Release.Status.DEPLOYING))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GO decision");

    Release noGo = release(Release.Status.GO_NO_GO, "deploy", "rollback", "tested", Release.GoDecision.NO_GO);
    assertThatThrownBy(() -> noGo.transition(Release.Status.DEPLOYING))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void deployment_stamps_the_actual_window() {
    Release ready = release(Release.Status.GO_NO_GO, "deploy", "rollback", "tested", Release.GoDecision.GO);
    Release deploying = ready.transition(Release.Status.DEPLOYING);
    assertThat(deploying.actualStart()).isNotNull();
    assertThat(deploying.actualEnd()).isNull();

    Release deployed = deploying.transition(Release.Status.DEPLOYED);
    assertThat(deployed.actualEnd()).isNotNull();
    assertThat(deployed.actualStart()).isEqualTo(deploying.actualStart());
  }

  @Test
  void a_deployed_release_can_still_be_rolled_back() {
    Release deployed = release(Release.Status.DEPLOYED, "deploy", "rollback", "tested", Release.GoDecision.GO);
    assertThat(deployed.transition(Release.Status.ROLLED_BACK).status())
        .isEqualTo(Release.Status.ROLLED_BACK);
  }

  @Test
  void terminal_states_are_final() {
    Release closed = release(Release.Status.CLOSED, "deploy", "rollback", "tested", Release.GoDecision.GO);
    assertThat(closed.terminal()).isTrue();
    assertThatThrownBy(() -> closed.transition(Release.Status.PLANNING))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void the_go_decision_is_only_recorded_during_the_review() {
    Release build = release(Release.Status.BUILD, "deploy", "rollback", null, null);
    assertThatThrownBy(() -> build.withGoDecision(
        Release.GoDecision.GO, "ship it", "carol", Instant.now()))
        .isInstanceOf(IllegalStateException.class);

    Release review = release(Release.Status.GO_NO_GO, "deploy", "rollback", "tested", null);
    Release decided = review.withGoDecision(Release.GoDecision.GO, "ship it", "carol", Instant.now());
    assertThat(decided.goDecision()).isEqualTo(Release.GoDecision.GO);
    assertThat(decided.goDecidedBy()).isEqualTo("carol");
  }

  @Test
  void content_freezes_once_deployment_starts() {
    assertThat(release(Release.Status.TESTING, "d", "r", "t", null).contentFrozen()).isFalse();
    assertThat(release(Release.Status.DEPLOYING, "d", "r", "t", Release.GoDecision.GO).contentFrozen()).isTrue();
    assertThat(release(Release.Status.CLOSED, "d", "r", "t", Release.GoDecision.GO).contentFrozen()).isTrue();
  }
}
