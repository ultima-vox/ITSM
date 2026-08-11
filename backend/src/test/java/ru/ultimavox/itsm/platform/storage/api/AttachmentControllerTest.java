package ru.ultimavox.itsm.platform.storage.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.storage.Attachment;
import ru.ultimavox.itsm.platform.storage.AttachmentService;
import ru.ultimavox.itsm.platform.storage.ScanStatus;

class AttachmentControllerTest {
  @Test
  void metadataAllowsExplicitParentGrant() {
    AttachmentService service = mock(AttachmentService.class);
    AccessControl access = mock(AccessControl.class);
    Authentication authentication = mock(Authentication.class);
    UUID id = UUID.randomUUID();
    Attachment attachment = new Attachment(
        id, "evidence.txt", "text/plain", 4, "objects/key", "uploader-1",
        Instant.now(), ScanStatus.CLEAN, "scanner", null, Instant.now());
    when(service.findById(id)).thenReturn(Optional.of(attachment));
    when(authentication.getName()).thenReturn("reader-2");
    when(service.canRead(id, "reader-2")).thenReturn(true);

    new AttachmentController(service, access).metadata(authentication, id);

    verify(access).require("reader-2", "attachment.read", "attachment", id.toString());
    verify(service).canRead(id, "reader-2");
  }

  @Test
  void metadataDeniesUnrelatedReader() {
    AttachmentService service = mock(AttachmentService.class);
    AccessControl access = mock(AccessControl.class);
    Authentication authentication = mock(Authentication.class);
    UUID id = UUID.randomUUID();
    Attachment attachment = new Attachment(
        id, "evidence.txt", "text/plain", 4, "objects/key", "uploader-1",
        Instant.now(), ScanStatus.CLEAN, "scanner", null, Instant.now());
    when(service.findById(id)).thenReturn(Optional.of(attachment));
    when(authentication.getName()).thenReturn("unrelated-reader");

    assertThatThrownBy(() -> new AttachmentController(service, access).metadata(authentication, id))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
        .hasMessageContaining("parent access");
  }
}
