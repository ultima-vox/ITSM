package ru.ultimavox.itsm.platform.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes notifications older than the configured retention window.
 * Default: 90 days. Disable by setting retention-days &lt;= 0.
 */
@Component
public class NotificationRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);

  private final NotificationStore store;
  private final int retentionDays;

  public NotificationRetentionJob(
      NotificationStore store,
      @Value("${itsm.notifications.retention-days:90}") int retentionDays
  ) {
    this.store = store;
    this.retentionDays = retentionDays;
  }

  @Scheduled(cron = "${itsm.notifications.retention-cron:0 30 3 * * *}")
  public void purgeExpired() {
    if (retentionDays <= 0) {
      return;
    }
    Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    int deleted = store.deleteOlderThan(cutoff);
    if (deleted > 0) {
      log.info("notification retention purged={} cutoff={}", deleted, cutoff);
    }
  }
}
