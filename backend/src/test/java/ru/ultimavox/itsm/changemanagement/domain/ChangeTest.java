package ru.ultimavox.itsm.changemanagement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangeTest {
  private Change draft() {
    return new Change(
        UUID.randomUUID(),
        "CHG-1001",
        Change.Type.NORMAL,
        Change.Risk.HIGH,
        Change.Status.DRAFT,
        "Deploy blue-green",
        Instant.parse("2026-08-10T20:00:00Z"),
        Instant.parse("2026-08-10T21:00:00Z"),
        "Deploy blue-green",
        "Return traffic to blue",
        "Security patch",
        null,
        null
    );
  }

  @Test
  void normal_change_cannot_skip_to_scheduled() {
    assertThatThrownBy(() -> draft().transition(Change.Status.SCHEDULED))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not allowed");
  }

  @Test
  void cannot_jump_from_draft_to_approved() {
    assertThatThrownBy(() -> draft().transition(Change.Status.APPROVED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void happy_path_reaches_scheduled_only_after_approval() {
    Change approved = draft()
        .transition(Change.Status.SUBMITTED)
        .transition(Change.Status.CAB_REVIEW)
        .withCabAssessment("CAB ok", Change.Risk.MEDIUM)
        .transition(Change.Status.APPROVED);

    assertThat(approved.status()).isEqualTo(Change.Status.APPROVED);
    assertThat(approved.transition(Change.Status.SCHEDULED).status()).isEqualTo(Change.Status.SCHEDULED);
  }

  @Test
  void cab_review_may_reject() {
    Change rejected = draft()
        .transition(Change.Status.SUBMITTED)
        .transition(Change.Status.CAB_REVIEW)
        .transition(Change.Status.REJECTED);

    assertThat(rejected.status()).isEqualTo(Change.Status.REJECTED);
    assertThatThrownBy(() -> rejected.transition(Change.Status.APPROVED))
        .isInstanceOf(IllegalStateException.class);
  }
}
