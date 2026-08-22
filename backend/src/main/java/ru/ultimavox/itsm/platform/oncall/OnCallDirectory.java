package ru.ultimavox.itsm.platform.oncall;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Who answers right now, and who is escalated to next. */
public interface OnCallDirectory {
  /** The subject on call for a schedule at {@code at}, honouring overrides. */
  Optional<String> onCall(String scheduleKey, Instant at);

  /**
   * The ordered escalation chain of a policy, each step already resolved to a subject.
   * Steps whose schedule has nobody on call are skipped rather than silently dropping the alert.
   */
  List<Responder> escalationChain(String policyKey, Instant at);

  /** The first responder of a policy — the one paged immediately. */
  default Optional<Responder> firstResponder(String policyKey, Instant at) {
    return escalationChain(policyKey, at).stream().findFirst();
  }

  record Responder(int stepOrder, int delayMinutes, String subject, String source) {}
}
