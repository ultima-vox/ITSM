package ru.ultimavox.itsm.platform.sla;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

/**
 * Sweeps SLA clocks for warning windows and breaches on a fixed delay. Each organization is
 * processed in its own scope so emitted {@code sla.warning}/{@code sla.breached} events carry
 * the correct tenant and reach that tenant's automation rules. Breaches take precedence over
 * warnings: a clock past due is marked BREACHED, not warned.
 */
@Component
class SlaBreachScheduler {

  private static final Logger log = LoggerFactory.getLogger(SlaBreachScheduler.class);

  private final SlaClockRepository clocks;
  private final SlaService sla;

  SlaBreachScheduler(SlaClockRepository clocks, SlaService sla) {
    this.clocks = clocks;
    this.sla = sla;
  }

  @Scheduled(
      fixedDelayString = "${itsm.sla.breach-interval:PT1M}",
      initialDelayString = "${itsm.sla.breach-initial-delay:PT1M}")
  void sweep() {
    for (String orgId : clocks.distinctOrgIdsWithDueOrWarnClocks()) {
      try {
        OrganizationContext.runAs(orgId, () -> {
          sla.detectBreaches(200);
          sla.detectWarnings(200);
          return null;
        });
      } catch (RuntimeException ex) {
        log.warn("SLA sweep failed for organization {}: {}", orgId, ex.getMessage());
      }
    }
  }
}
