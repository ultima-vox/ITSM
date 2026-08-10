package ru.ultimavox.itsm.servicedesk.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.storage.Attachment;
import ru.ultimavox.itsm.platform.storage.AttachmentService;
import ru.ultimavox.itsm.platform.storage.JdbcAttachmentRepository;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

/**
 * Persists work-item ↔ attachment links. Upload remains on platform AttachmentService;
 * this module owns the association.
 */
@Service
public class WorkItemAttachmentService {

  private final JdbcTemplate jdbc;
  private final WorkItemStore store;
  private final AttachmentService attachments;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public WorkItemAttachmentService(
      JdbcTemplate jdbc,
      WorkItemStore store,
      AttachmentService attachments,
      AuditTrail audit,
      IntegrationEventOutbox outbox
  ) {
    this.jdbc = jdbc;
    this.store = store;
    this.attachments = attachments;
    this.audit = audit;
    this.outbox = outbox;
  }

  public List<LinkedAttachment> list(UUID workItemId) {
    store.requireById(workItemId);
    return jdbc.query(
        """
        SELECT a.id, a.filename, a.content_type, a.size_bytes, a.storage_key, a.uploaded_by, a.created_at,
               a.scan_status, a.scan_engine, a.scan_detail, a.scanned_at,
               l.linked_by, l.linked_at
        FROM work_item_attachment l
        JOIN attachment a ON a.id = l.attachment_id
        WHERE l.work_item_id = ?
        ORDER BY l.linked_at DESC
        """,
        (rs, i) -> new LinkedAttachment(
            JdbcAttachmentRepository.mapRow(rs),
            rs.getString("linked_by"),
            rs.getTimestamp("linked_at").toInstant()
        ),
        workItemId
    );
  }

  @Transactional
  public LinkedAttachment link(UUID workItemId, UUID attachmentId, String actorId) {
    WorkItem item = store.requireById(workItemId);
    Attachment attachment = attachments.findById(attachmentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));

    Instant now = Instant.now();
    int inserted = jdbc.update(
        """
        INSERT INTO work_item_attachment (work_item_id, attachment_id, linked_by, linked_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (work_item_id, attachment_id) DO NOTHING
        """,
        workItemId,
        attachmentId,
        actorId,
        Timestamp.from(now)
    );

    if (inserted == 0) {
      // Already linked — return existing
      return list(workItemId).stream()
          .filter(l -> l.attachment().id().equals(attachmentId))
          .findFirst()
          .orElse(new LinkedAttachment(attachment, actorId, now));
    }

    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    Map<String, Object> after = Map.of(
        "attachmentId", attachmentId.toString(),
        "filename", attachment.filename(),
        "workItemNumber", item.number()
    );
    audit.append(new AuditTrail.Entry(
        actorId,
        "work-item.attachment.linked",
        "work-item",
        workItemId.toString(),
        Map.of(),
        after,
        correlationId,
        now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(),
        "work-item.attachment.linked",
        1,
        now,
        correlationId,
        "work-item",
        workItemId.toString(),
        after
    ));

    return new LinkedAttachment(attachment, actorId, now);
  }

  @Transactional
  public void unlink(UUID workItemId, UUID attachmentId, String actorId) {
    store.requireById(workItemId);
    int deleted = jdbc.update(
        "DELETE FROM work_item_attachment WHERE work_item_id = ? AND attachment_id = ?",
        workItemId,
        attachmentId
    );
    if (deleted == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment link not found");
    }

    Instant now = Instant.now();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    Map<String, Object> after = Map.of("attachmentId", attachmentId.toString());
    audit.append(new AuditTrail.Entry(
        actorId,
        "work-item.attachment.unlinked",
        "work-item",
        workItemId.toString(),
        Map.of(),
        after,
        correlationId,
        now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(),
        "work-item.attachment.unlinked",
        1,
        now,
        correlationId,
        "work-item",
        workItemId.toString(),
        after
    ));
  }

  public record LinkedAttachment(Attachment attachment, String linkedBy, Instant linkedAt) {}
}
