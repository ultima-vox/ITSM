package ru.ultimavox.itsm.platform.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Object-storage port for attachments. Implementations: local metadata-only (dev) or
 * S3-compatible (MinIO / AWS). Callers never depend on the backend vendor.
 */
public interface AttachmentStorage {

  /**
   * Stores (or replaces) an object. Local/dev adapters may persist metadata only
   * and ignore byte content.
   */
  StoredAttachment store(StoreRequest request);

  Optional<StoredAttachment> find(String objectKey);

  void delete(String objectKey);

  /**
   * Opens a readable stream for the stored object when the backend holds bytes.
   * Local metadata-only adapter returns empty; S3 returns a stream that the caller must close.
   */
  default Optional<InputStream> openContent(String objectKey) {
    return Optional.empty();
  }

  record StoreRequest(
      String objectKey,
      String contentType,
      long sizeBytes,
      String originalFilename,
      InputStream content
  ) {
    public StoreRequest {
      if (objectKey == null || objectKey.isBlank()) {
        throw new IllegalArgumentException("objectKey is required");
      }
      if (sizeBytes < 0) {
        throw new IllegalArgumentException("sizeBytes must be >= 0");
      }
    }
  }

  record StoredAttachment(
      String objectKey,
      String contentType,
      long sizeBytes,
      String originalFilename,
      String storageUri,
      String backend
  ) {}
}
