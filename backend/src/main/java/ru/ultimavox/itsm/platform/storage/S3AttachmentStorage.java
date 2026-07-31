package ru.ultimavox.itsm.platform.storage;

import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3-compatible attachment adapter (AWS S3, MinIO). Uses path-style addressing when configured
 * for MinIO. Does not auto-create the bucket — provision via ops/terraform.
 */
public final class S3AttachmentStorage implements AttachmentStorage, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(S3AttachmentStorage.class);
  static final String BACKEND = "s3";

  private final S3Client client;
  private final String bucket;
  private final String endpoint;

  S3AttachmentStorage(ItsmStorageProperties.S3 props) {
    this.bucket = props.getBucket();
    this.endpoint = props.getEndpoint();
    this.client = S3Client.builder()
        .endpointOverride(URI.create(props.getEndpoint()))
        .region(Region.of(props.getRegion() == null || props.getRegion().isBlank()
            ? "us-east-1"
            : props.getRegion()))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
        .serviceConfiguration(S3Configuration.builder()
            .pathStyleAccessEnabled(props.isPathStyleAccess())
            .build())
        .build();
  }

  /** Test / custom client constructor. */
  S3AttachmentStorage(S3Client client, String bucket, String endpoint) {
    this.client = client;
    this.bucket = bucket;
    this.endpoint = endpoint;
  }

  @Override
  public StoredAttachment store(StoreRequest request) {
    String contentType = request.contentType() == null
        ? "application/octet-stream"
        : request.contentType();
    PutObjectRequest put = PutObjectRequest.builder()
        .bucket(bucket)
        .key(request.objectKey())
        .contentType(contentType)
        .contentLength(request.sizeBytes())
        .build();
    InputStream content = request.content() == null ? InputStream.nullInputStream() : request.content();
    client.putObject(put, RequestBody.fromInputStream(content, request.sizeBytes()));
    log.debug("attachment s3 store bucket={} key={}", bucket, request.objectKey());
    return new StoredAttachment(
        request.objectKey(),
        contentType,
        request.sizeBytes(),
        request.originalFilename(),
        storageUri(request.objectKey()),
        BACKEND
    );
  }

  @Override
  public Optional<StoredAttachment> find(String objectKey) {
    try {
      HeadObjectResponse head = client.headObject(HeadObjectRequest.builder()
          .bucket(bucket)
          .key(objectKey)
          .build());
      return Optional.of(new StoredAttachment(
          objectKey,
          head.contentType() == null ? "application/octet-stream" : head.contentType(),
          head.contentLength() == null ? 0L : head.contentLength(),
          null,
          storageUri(objectKey),
          BACKEND
      ));
    } catch (NoSuchKeyException ex) {
      return Optional.empty();
    } catch (S3Exception ex) {
      if (ex.statusCode() == 404) {
        return Optional.empty();
      }
      throw ex;
    }
  }

  @Override
  public void delete(String objectKey) {
    client.deleteObject(DeleteObjectRequest.builder()
        .bucket(bucket)
        .key(objectKey)
        .build());
    log.debug("attachment s3 delete bucket={} key={}", bucket, objectKey);
  }

  @Override
  public Optional<InputStream> openContent(String objectKey) {
    try {
      ResponseInputStream<GetObjectResponse> stream = client.getObject(GetObjectRequest.builder()
          .bucket(bucket)
          .key(objectKey)
          .build());
      return Optional.of(stream);
    } catch (NoSuchKeyException ex) {
      return Optional.empty();
    } catch (S3Exception ex) {
      if (ex.statusCode() == 404) {
        return Optional.empty();
      }
      throw ex;
    }
  }

  @Override
  public void close() {
    client.close();
  }

  private String storageUri(String objectKey) {
    String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    return base + "/" + bucket + "/" + objectKey;
  }
}
