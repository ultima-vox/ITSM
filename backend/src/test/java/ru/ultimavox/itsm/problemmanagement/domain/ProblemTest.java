package ru.ultimavox.itsm.problemmanagement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProblemTest {
  private Problem neu() {
    return new Problem(UUID.randomUUID(), "PRB-1", "VPN flaps", Problem.Status.NEW, null, null, Set.of());
  }

  @Test
  void cannot_skip_investigation() {
    assertThatThrownBy(() -> neu().transition(Problem.Status.RESOLVED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void known_error_requires_workaround() {
    var investigating = neu().transition(Problem.Status.UNDER_INVESTIGATION);
    assertThatThrownBy(() -> investigating.transition(Problem.Status.KNOWN_ERROR))
        .hasMessageContaining("Workaround");
  }

  @Test
  void happy_path_to_closed() {
    Problem closed = neu()
        .transition(Problem.Status.UNDER_INVESTIGATION)
        .withInvestigationNotes("Gateway overload", "Failover region")
        .transition(Problem.Status.ROOT_CAUSE_IDENTIFIED)
        .transition(Problem.Status.KNOWN_ERROR)
        .transition(Problem.Status.RESOLVED)
        .transition(Problem.Status.CLOSED);
    assertThat(closed.status()).isEqualTo(Problem.Status.CLOSED);
  }
}
