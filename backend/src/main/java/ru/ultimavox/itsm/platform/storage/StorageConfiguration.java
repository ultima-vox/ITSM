package ru.ultimavox.itsm.platform.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Selects {@link AttachmentStorage}: local metadata-only (default) or S3-compatible (MinIO).
 */
@Configuration
@EnableConfigurationProperties(ItsmStorageProperties.class)
class StorageConfiguration {

  private static final Logger log = LoggerFactory.getLogger(StorageConfiguration.class);

  @Bean
  @Primary
  MalwareScanPort malwareScanPort(
      ContentSignatureMalwareScan signature,
      ObjectProvider<ClamAvMalwareScan> clamav
  ) {
    ClamAvMalwareScan engine = clamav.getIfAvailable();
    if (engine == null) {
      log.info("Malware scan engine=content-signature (ClamAV disabled)");
      return signature;
    }
    log.info("Malware scan engine=content-signature+clamav");
    return new CompositeMalwareScan(signature, engine);
  }

  @Bean(destroyMethod = "closeIfNeeded")
  AttachmentStorage attachmentStorage(ItsmStorageProperties props) {
    if (props.isS3()) {
      log.info(
          "Attachment storage backend=s3 endpoint={} bucket={}",
          props.getS3().getEndpoint(),
          props.getS3().getBucket()
      );
      S3AttachmentStorage s3 = new S3AttachmentStorage(props.getS3());
      return new CloseableAttachmentStorage(s3);
    }
    log.info("Attachment storage backend=local-metadata (dev); set itsm.storage.type=s3 for MinIO/S3");
    return new CloseableAttachmentStorage(new LocalMetadataAttachmentStorage());
  }

  /**
   * Wrapper so Spring can always invoke a no-arg-compatible destroy method name while only
   * S3 actually closes an SDK client.
   */
  static final class CloseableAttachmentStorage implements AttachmentStorage {

    private final AttachmentStorage delegate;

    CloseableAttachmentStorage(AttachmentStorage delegate) {
      this.delegate = delegate;
    }

    AttachmentStorage delegate() {
      return delegate;
    }

    @Override
    public StoredAttachment store(StoreRequest request) {
      return delegate.store(request);
    }

    @Override
    public java.util.Optional<StoredAttachment> find(String objectKey) {
      return delegate.find(objectKey);
    }

    @Override
    public void delete(String objectKey) {
      delegate.delete(objectKey);
    }

    @Override
    public java.util.Optional<java.io.InputStream> openContent(String objectKey) {
      return delegate.openContent(objectKey);
    }

    void closeIfNeeded() {
      if (delegate instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception ex) {
          log.warn("Failed to close attachment storage: {}", ex.toString());
        }
      }
    }
  }
}
