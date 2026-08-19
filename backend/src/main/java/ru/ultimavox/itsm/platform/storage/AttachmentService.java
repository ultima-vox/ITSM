package ru.ultimavox.itsm.platform.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Service
public class AttachmentService {

  /** Bytes read for signature scan before streaming remainder to storage. */
  static final int SCAN_PREFIX_BYTES = 256 * 1024;

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
    if (sizeBytes < 0) {
      throw new IllegalArgumentException("sizeBytes must be >= 0");
    }

    UUID id = UUID.randomUUID();
    String storageKey = "organizations/" + sanitizeOrganization(OrganizationContext.current())
        + "/attachments/" + id + "/" + sanitizeFilename(filename);
    String type = contentType == null || contentType.isBlank()
        ? "application/octet-stream"
        : contentType;

    PrefixAndStream prepared = readPrefix(content, sizeBytes);
    Instant now = Instant.now();
    Attachment pending = new Attachment(
        id,
        filename,
        type,
        sizeBytes,
        storageKey,
        uploadedBy,
        now,
        ScanStatus.PENDING,
        "pending",
        "quarantine until scanned",
        null
    );
    repository.save(pending);

    // Store bytes even when later marked infected so quarantine retains the object.
    storage.store(new AttachmentStorage.StoreRequest(
        storageKey,
        type,
        sizeBytes,
        filename,
        prepared.forStore()
    ));

    MalwareScanPort.ScanResult scan = malwareScan.scan(
        new MalwareScanPort.ScanRequest(
            filename,
            type,
            sizeBytes,
            storageKey,
            prepared.prefix()
        )
    );
    Instant scannedAt = scan.status() == ScanStatus.PENDING ? null : Instant.now();
    repository.updateScan(id, scan.status(), scan.engine(), scan.detail(), scannedAt);
    return new Attachment(
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
        scannedAt
    );
  }

  public Attachment rescan(Attachment attachment) {
    byte[] prefix = new byte[0];
    try (InputStream stored = storage.openContent(attachment.storageKey()).orElse(null)) {
      if (stored != null) {
        prefix = stored.readNBytes(SCAN_PREFIX_BYTES);
      }
    } catch (IOException ex) {
      repository.updateScan(
          attachment.id(),
          ScanStatus.PENDING,
          attachment.scanEngine(),
          "rescan read failed: " + ex.getMessage(),
          null
      );
      return attachment;
    }
    MalwareScanPort.ScanResult scan = malwareScan.scan(
        new MalwareScanPort.ScanRequest(
            attachment.filename(),
            attachment.contentType(),
            attachment.sizeBytes(),
            attachment.storageKey(),
            prefix
        )
    );
    Instant scannedAt = scan.status() == ScanStatus.PENDING ? null : Instant.now();
    repository.updateScan(attachment.id(), scan.status(), scan.engine(), scan.detail(), scannedAt);
    return new Attachment(
        attachment.id(),
        attachment.filename(),
        attachment.contentType(),
        attachment.sizeBytes(),
        attachment.storageKey(),
        attachment.uploadedBy(),
        attachment.createdAt(),
        scan.status(),
        scan.engine(),
        scan.detail(),
        scannedAt
    );
  }

  public Optional<Attachment> findById(UUID id) {
    return repository.findById(id);
  }

  public boolean canRead(UUID id, String subjectId) {
    return repository.canRead(id, subjectId);
  }

  public void grantRead(UUID id, String subjectId, String sourceType, String sourceId,
                        String grantedBy, Instant createdAt) {
    repository.grantRead(id, subjectId, sourceType, sourceId, grantedBy, createdAt);
  }

  public void revokeSource(UUID id, String subjectId, String sourceType, String sourceId) {
    repository.revokeSource(id, subjectId, sourceType, sourceId);
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

  private static String sanitizeOrganization(String organizationId) {
    String sanitized = organizationId == null
        ? "default"
        : organizationId.replaceAll("[^a-zA-Z0-9._-]", "_");
    return sanitized.isBlank() ? "default" : sanitized;
  }

  /**
   * Reads up to {@link #SCAN_PREFIX_BYTES} for scanning and returns a stream that still
   * yields the full original content for storage.
   */
  static PrefixAndStream readPrefix(InputStream content, long sizeBytes) {
    if (content == null) {
      return new PrefixAndStream(new byte[0], InputStream.nullInputStream());
    }
    try {
      int toRead = SCAN_PREFIX_BYTES;
      if (sizeBytes >= 0 && sizeBytes < toRead) {
        toRead = (int) sizeBytes;
      }
      byte[] buf = content.readNBytes(toRead);
      if (buf.length < SCAN_PREFIX_BYTES && sizeBytes > buf.length) {
        // stream ended early — still fine
      }
      if (buf.length < SCAN_PREFIX_BYTES || (sizeBytes >= 0 && sizeBytes <= buf.length)) {
        return new PrefixAndStream(buf, new ByteArrayInputStream(buf));
      }
      InputStream forStore = new SequenceInputStream(
          new ByteArrayInputStream(buf),
          content
      );
      return new PrefixAndStream(buf, forStore);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to read attachment content for scan", ex);
    }
  }

  record PrefixAndStream(byte[] prefix, InputStream forStore) {}
}
