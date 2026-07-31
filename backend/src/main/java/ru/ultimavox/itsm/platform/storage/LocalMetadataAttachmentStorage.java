package ru.ultimavox.itsm.platform.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev/default adapter: records attachment metadata only and discards bytes.
 * Suitable when MinIO/S3 is not available; not for production content durability.
 */
public final class LocalMetadataAttachmentStorage implements AttachmentStorage {

  private static final Logger log = LoggerFactory.getLogger(LocalMetadataAttachmentStorage.class);
  static final String BACKEND = "local-metadata";

  private final ConcurrentMap<String, StoredAttachment> store = new ConcurrentHashMap<>();

  @Override
  public StoredAttachment store(StoreRequest request) {
    drainQuietly(request.content());
    StoredAttachment stored = new StoredAttachment(
        request.objectKey(),
        request.contentType() == null ? "application/octet-stream" : request.contentType(),
        request.sizeBytes(),
        request.originalFilename(),
        "local://" + request.objectKey(),
        BACKEND
    );
    store.put(request.objectKey(), stored);
    log.debug("attachment local-metadata store key={} size={}", request.objectKey(), request.sizeBytes());
    return stored;
  }

  @Override
  public Optional<StoredAttachment> find(String objectKey) {
    return Optional.ofNullable(store.get(objectKey));
  }

  @Override
  public void delete(String objectKey) {
    store.remove(objectKey);
    log.debug("attachment local-metadata delete key={}", objectKey);
  }

  private static void drainQuietly(InputStream content) {
    if (content == null) {
      return;
    }
    try (InputStream in = content) {
      in.transferTo(OutputNull.INSTANCE);
    } catch (IOException ex) {
      log.debug("Ignoring content drain failure in local storage: {}", ex.toString());
    }
  }

  /** Minimal discard sink without allocating a large buffer holder. */
  private static final class OutputNull extends java.io.OutputStream {
    static final OutputNull INSTANCE = new OutputNull();

    @Override
    public void write(int b) {
      // discard
    }

    @Override
    public void write(byte[] b, int off, int len) {
      // discard
    }
  }
}
