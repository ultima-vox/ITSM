package ru.ultimavox.itsm.platform.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AttachmentServiceTest {

  private AttachmentService service;
  private Map<UUID, Attachment> db;
  private LocalMetadataAttachmentStorage storage;

  @BeforeEach
  void setUp() {
    db = new ConcurrentHashMap<>();
    storage = new LocalMetadataAttachmentStorage();
    AttachmentRepository repo = new AttachmentRepository() {
      @Override
      public Attachment save(Attachment attachment) {
        db.put(attachment.id(), attachment);
        return attachment;
      }

      @Override
      public Optional<Attachment> findById(UUID id) {
        return Optional.ofNullable(db.get(id));
      }
    };
    service = new AttachmentService(storage, repo, new AllowlistMalwareScan());
  }

  @Test
  void upload_stores_metadata_and_object_key() {
    byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
    Attachment saved = service.upload(
        "agent-1",
        "report.pdf",
        "application/pdf",
        bytes.length,
        new ByteArrayInputStream(bytes)
    );

    assertThat(saved.id()).isNotNull();
    assertThat(saved.filename()).isEqualTo("report.pdf");
    assertThat(saved.contentType()).isEqualTo("application/pdf");
    assertThat(saved.sizeBytes()).isEqualTo(bytes.length);
    assertThat(saved.storageKey()).startsWith("attachments/" + saved.id() + "/");
    assertThat(saved.uploadedBy()).isEqualTo("agent-1");
    assertThat(saved.scanStatus()).isEqualTo(ScanStatus.CLEAN);
    assertThat(saved.scanEngine()).isEqualTo(AllowlistMalwareScan.ENGINE);
    assertThat(service.findById(saved.id())).contains(saved);
    assertThat(storage.find(saved.storageKey())).isPresent();
  }

  @Test
  void upload_marks_eicar_filename_infected() {
    byte[] bytes = "x".getBytes(StandardCharsets.UTF_8);
    Attachment saved = service.upload(
        "agent-1",
        "eicar.com",
        "application/octet-stream",
        bytes.length,
        new ByteArrayInputStream(bytes)
    );
    assertThat(saved.scanStatus()).isEqualTo(ScanStatus.INFECTED);
    assertThat(saved.isDownloadAllowed()).isFalse();
    assertThat(service.openContent(saved)).isEmpty();
  }

  @Test
  void upload_rejects_blank_filename() {
    assertThatThrownBy(() -> service.upload(
        "agent-1", "  ", "text/plain", 0, InputStreamNull.INSTANCE
    )).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void openContent_empty_for_local_metadata_backend() {
    byte[] bytes = "x".getBytes(StandardCharsets.UTF_8);
    Attachment saved = service.upload(
        "agent-1", "note.txt", "text/plain", bytes.length, new ByteArrayInputStream(bytes)
    );
    assertThat(service.openContent(saved)).isEmpty();
  }

  @Test
  void sanitizeFilename_strips_path_and_special_chars() {
    assertThat(AttachmentService.sanitizeFilename("../../etc/passwd")).isEqualTo("passwd");
    assertThat(AttachmentService.sanitizeFilename("a/b\\c.txt")).isEqualTo("c.txt");
    assertThat(AttachmentService.sanitizeFilename("ok-name_1.pdf")).isEqualTo("ok-name_1.pdf");
  }

  /** Null stream for zero-byte edge cases in rejection tests. */
  private static final class InputStreamNull extends java.io.InputStream {
    static final InputStreamNull INSTANCE = new InputStreamNull();

    @Override
    public int read() {
      return -1;
    }
  }
}
