package ru.ultimavox.itsm.platform.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sweeps due automation action retries on a fixed delay. The sweep is transactional and uses
 * {@code FOR UPDATE SKIP LOCKED}, so concurrent instances never double-process a row; a failing
 * retry is contained as a retry-table update rather than a rolled-back sweep.
 */
@Component
class AutomationActionRetryScheduler {

  private static final Logger log = LoggerFactory.getLogger(AutomationActionRetryScheduler.class);

  private final AutomationActionRetryService retry;

  AutomationActionRetryScheduler(AutomationActionRetryService retry) {
    this.retry = retry;
  }

  @Scheduled(
      fixedDelayString = "${itsm.automation.retry-interval:PT1M}",
      initialDelayString = "${itsm.automation.retry-initial-delay:PT1M}")
  void sweep() {
    try {
      retry.retryDue(100);
    } catch (RuntimeException ex) {
      log.warn("Automation retry sweep failed: {}", ex.getMessage());
    }
  }
}
