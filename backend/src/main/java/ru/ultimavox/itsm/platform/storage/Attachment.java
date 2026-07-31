package ru.ultimavox.itsm.platform.storage;

import java.time.Instant;
import java.util.UUID;

/** Persisted attachment metadata (bytes live in {@link AttachmentStorage}). */
public record Attachment(
    UUID id,
    String filename,
    String contentType,
    long sizeBytes,
    String storageKey,
    String uploadedBy,
    Instant createdAt,
    ScanStatus scanStatus,
    String scanEngine,
    String scanDetail,
    Instant scannedAt
) {
  public Attachment {
    if (scanStatus == null) {
      scanStatus = ScanStatus.PENDING;
    }
  }

  public boolean isDownloadAllowed() {
    return scanStatus == ScanStatus.CLEAN || scanStatus == ScanStatus.SKIPPED;
  }
}
