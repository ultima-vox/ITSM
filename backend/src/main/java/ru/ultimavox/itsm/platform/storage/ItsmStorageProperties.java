package ru.ultimavox.itsm.platform.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Attachment storage settings. Default {@code type=local} needs no external process.
 * Set {@code type=s3} with MinIO/AWS endpoint for production-shaped object storage.
 */
@ConfigurationProperties(prefix = "itsm.storage")
public class ItsmStorageProperties {

  /** {@code local} (metadata-only) or {@code s3} (S3-compatible / MinIO). */
  private String type = "local";

  private final S3 s3 = new S3();

  private final ClamAv clamav = new ClamAv();

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public S3 getS3() {
    return s3;
  }

  public ClamAv getClamav() {
    return clamav;
  }

  public boolean isS3() {
    return "s3".equalsIgnoreCase(type);
  }

  public static class S3 {
    /** e.g. http://localhost:9000 for MinIO */
    private String endpoint = "http://localhost:9000";

    private String bucket = "itsm-attachments";

    private String accessKey = "minioadmin";

    private String secretKey = "minioadmin";

    private String region = "us-east-1";

    /** Required for MinIO path-style addressing. */
    private boolean pathStyleAccess = true;

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public String getBucket() {
      return bucket;
    }

    public void setBucket(String bucket) {
      this.bucket = bucket;
    }

    public String getAccessKey() {
      return accessKey;
    }

    public void setAccessKey(String accessKey) {
      this.accessKey = accessKey;
    }

    public String getSecretKey() {
      return secretKey;
    }

    public void setSecretKey(String secretKey) {
      this.secretKey = secretKey;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public boolean isPathStyleAccess() {
      return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
      this.pathStyleAccess = pathStyleAccess;
    }
  }

  public static class ClamAv {
    private boolean enabled = false;
    private String host = "localhost";
    private int port = 3310;
    private java.time.Duration connectTimeout = java.time.Duration.ofSeconds(2);
    private java.time.Duration readTimeout = java.time.Duration.ofSeconds(30);

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public java.time.Duration getConnectTimeout() {
      return connectTimeout;
    }

    public void setConnectTimeout(java.time.Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    public java.time.Duration getReadTimeout() {
      return readTimeout;
    }

    public void setReadTimeout(java.time.Duration readTimeout) {
      this.readTimeout = readTimeout;
    }
  }
}
