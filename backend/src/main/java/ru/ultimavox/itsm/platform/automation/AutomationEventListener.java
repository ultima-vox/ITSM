package ru.ultimavox.itsm.platform.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.ultimavox.itsm.platform.event.AutomationDepthContext;
import ru.ultimavox.itsm.platform.event.DomainEventEnvelope;

/**
 * Bridges committed domain events to the {@link AutomationRunner}.
 *
 * <p>Executes after the originating transaction commits so automation never participates in
 * (or rolls back) the producing mutation. The action log keeps execution idempotent, the
 * automation-depth guard bounds cascades, and all failures are contained so a downstream
 * problem can never fail the original request after it has committed.
 */
@Component
class AutomationEventListener {

  private static final Logger log = LoggerFactory.getLogger(AutomationEventListener.class);

  private final AutomationRunner runner;

  AutomationEventListener(AutomationRunner runner) {
    this.runner = runner;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onCommittedDomainEvent(DomainEventEnvelope envelope) {
    int depth = envelope.automationDepth();
    if (depth >= AutomationDepthContext.MAX_AUTOMATION_DEPTH) {
      log.warn("Automation loop guard: ignoring event {} produced at automation depth {}",
          envelope.event().id(), depth);
      return;
    }
    try {
      int executed = AutomationDepthContext.atDepth(depth + 1, () -> runner.handle(envelope.event()));
      if (executed > 0) {
        log.debug("Automation executed {} actions for event {}", executed, envelope.event().id());
      }
    } catch (RuntimeException failure) {
      log.error("Automation processing failed for event {}: {}",
          envelope.event().id(), failure.toString());
    }
  }
}
