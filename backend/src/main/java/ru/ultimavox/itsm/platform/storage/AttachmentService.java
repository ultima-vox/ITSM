package ru.ultimavox.itsm.platform.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

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
    String storageKey = "attachments/" + id + "/" + sanitizeFilename(filename);
    String type = contentType == null || contentType.isBlank()
        ? "application/octet-stream"
        : contentType;

    PrefixAndStream prepared = readPrefix(content, sizeBytes);
    MalwareScanPort.ScanResult scan = malwareScan.scan(
        new MalwareScanPort.ScanRequest(
            filename,
            type,
            sizeBytes,
            storageKey,
            prepared.prefix()
        )
    );

    // Store bytes even when infected so quarantine / forensics retain the object;
    // download remains blocked via scan_status.
    storage.store(new AttachmentStorage.StoreRequest(
        storageKey,
        type,
        sizeBytes,
        filename,
        prepared.forStore()
    ));

    Instant now = Instant.now();
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
