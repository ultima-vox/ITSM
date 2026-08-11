package ru.ultimavox.itsm.platform.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class IdempotencyRetentionJob {
  private final JdbcTemplate jdbc;

  IdempotencyRetentionJob(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Scheduled(cron = "${itsm.idempotency.retention-cron:0 15 3 * * *}")
  @Transactional
  void deleteExpired() {
    jdbc.update("DELETE FROM api_idempotency_record WHERE expires_at < now()");
  }
}
