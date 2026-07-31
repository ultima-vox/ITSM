package ru.ultimavox.itsm.platform.storage;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AttachmentService {

  private final AttachmentStorage storage;
  private final AttachmentRepository repository;
  private final MalwareScanPort malwareScan;

  public AttachmentService(
      AttachmentStorage storage,
      AttachmentRepository repository,
      MalwareScanPort malwareScan
  ) {
    this.storage = storage;
    this.repository = repository;
    this.malwareScan = malwareScan;
  }

  public Attachment upload(
      String uploadedBy,
      String filename,
      String contentType,
      long sizeBytes,
      InputStream content
  ) {
    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("filename is required");
    }
    UUID id = UUID.randomUUID();
    String storageKey = "attachments/" + id + "/" + sanitizeFilename(filename);
    String type = contentType == null || contentType.isBlank()
        ? "application/octet-stream"
        : contentType;

    storage.store(new AttachmentStorage.StoreRequest(
        storageKey,
        type,
        sizeBytes,
        filename,
        content
    ));

    Instant now = Instant.now();
    MalwareScanPort.ScanResult scan = malwareScan.scan(
        new MalwareScanPort.ScanRequest(filename, type, sizeBytes, storageKey)
    );

    Attachment attachment = new Attachment(
        id,
        filename,
        type,
        sizeBytes,
        storageKey,
        uploadedBy,
        now,
        scan.status(),
        scan.engine(),
        scan.detail(),
        now
    );
    return repository.save(attachment);
  }

  public Optional<Attachment> findById(UUID id) {
    return repository.findById(id);
  }

  /**
   * Opens content stream when the storage backend holds bytes and scan allows download.
   * Caller must close the stream.
   */
  public Optional<InputStream> openContent(Attachment attachment) {
    if (!attachment.isDownloadAllowed()) {
      return Optional.empty();
    }
    return storage.openContent(attachment.storageKey());
  }

  static String sanitizeFilename(String filename) {
    String name = filename.replace('\\', '/');
    int slash = name.lastIndexOf('/');
    if (slash >= 0) {
      name = name.substring(slash + 1);
    }
    name = name.replaceAll("[^a-zA-Z0-9._\\- ]", "_").trim();
    if (name.isBlank()) {
      return "file";
    }
    return name.length() > 200 ? name.substring(0, 200) : name;
  }
}
