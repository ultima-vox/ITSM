package ru.ultimavox.itsm.platform.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Component
class AttachmentScanRetryJob {

  private static final Logger log = LoggerFactory.getLogger(AttachmentScanRetryJob.class);

  private final AttachmentRepository repository;
  private final AttachmentService attachments;

  AttachmentScanRetryJob(AttachmentRepository repository, AttachmentService attachments) {
    this.repository = repository;
    this.attachments = attachments;
  }

  @Scheduled(
      fixedDelayString = "${itsm.storage.scan-retry-interval:PT1M}",
      initialDelayString = "${itsm.storage.scan-retry-initial-delay:PT30S}")
  void retryPending() {
    for (String orgId : repository.distinctOrgIdsWithUnscanned()) {
      try {
        OrganizationContext.runAs(orgId, () -> {
          for (Attachment pending : repository.listUnscanned(50)) {
            attachments.rescan(pending);
          }
          return null;
        });
      } catch (RuntimeException ex) {
        log.warn("Attachment scan retry failed for organization {}: {}", orgId, ex.getMessage());
      }
    }
  }
}
