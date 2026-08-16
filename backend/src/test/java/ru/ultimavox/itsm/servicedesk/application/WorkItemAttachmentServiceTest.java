package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.storage.Attachment;
import ru.ultimavox.itsm.platform.storage.AttachmentService;
import ru.ultimavox.itsm.platform.storage.ScanStatus;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

@ExtendWith(MockitoExtension.class)
class WorkItemAttachmentServiceTest {

  @Mock JdbcTemplate jdbc;
  @Mock WorkItemStore store;
  @Mock AttachmentService attachments;
  @Mock AuditTrail audit;
  @Mock IntegrationEventOutbox outbox;

  WorkItemAttachmentService service;

  @BeforeEach
  void setUp() {
    service = new WorkItemAttachmentService(jdbc, store, attachments, audit, outbox);
  }

  @Test
  void link_inserts_and_audits() {
    UUID wi = UUID.randomUUID();
    UUID att = UUID.randomUUID();
    Instant now = Instant.now();
    WorkItem item = sample(wi, now);
    Attachment attachment = new Attachment(
        att, "shot.png", "image/png", 12, "k", "u1", now,
        ScanStatus.CLEAN, "allowlist-stub", "ok", now
    );

    when(store.requireById(wi)).thenReturn(item);
    when(attachments.findById(att)).thenReturn(Optional.of(attachment));
    when(jdbc.update(anyString(), eq(wi), eq(att), eq("actor"), any())).thenReturn(1);

    var linked = service.link(wi, att, "actor");

    assertThat(linked.attachment().filename()).isEqualTo("shot.png");
    verify(audit).append(any());
    verify(outbox).record(any());
  }

  @Test
  void list_maps_join_rows() {
    UUID wi = UUID.randomUUID();
    when(store.requireById(wi)).thenReturn(sample(wi, Instant.now()));
    when(jdbc.query(anyString(), any(RowMapper.class), eq(wi))).thenReturn(List.of());

    assertThat(service.list(wi)).isEmpty();
  }

  private static WorkItem sample(UUID id, Instant now) {
    return new WorkItem(
        id, "INC-1", Type.INCIDENT, "t", "d", "s", State.NEW, Priority.MEDIUM,
        Impact.MEDIUM, Urgency.MEDIUM, null, "req", null, null, null, false, now, now, null
    );
  }
}
