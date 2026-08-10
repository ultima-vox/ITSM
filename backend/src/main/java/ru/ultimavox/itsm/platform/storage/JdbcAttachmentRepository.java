package ru.ultimavox.itsm.platform.storage;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Repository
public class JdbcAttachmentRepository implements AttachmentRepository {

  private final JdbcTemplate jdbc;

  JdbcAttachmentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Attachment save(Attachment attachment) {
    jdbc.update(
        """
        INSERT INTO attachment (
          id, org_id, filename, content_type, size_bytes, storage_key, uploaded_by, created_at,
          scan_status, scan_engine, scan_detail, scanned_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        attachment.id(),
        OrganizationContext.current(),
        attachment.filename(),
        attachment.contentType(),
        attachment.sizeBytes(),
        attachment.storageKey(),
        attachment.uploadedBy(),
        Timestamp.from(attachment.createdAt()),
        attachment.scanStatus().name(),
        attachment.scanEngine(),
        attachment.scanDetail(),
        attachment.scannedAt() == null ? null : Timestamp.from(attachment.scannedAt())
    );
    return attachment;
  }

  @Override
  public Optional<Attachment> findById(UUID id) {
    List<Attachment> rows = jdbc.query(
        """
        SELECT id, filename, content_type, size_bytes, storage_key, uploaded_by, created_at,
               scan_status, scan_engine, scan_detail, scanned_at
        FROM attachment
        WHERE id = ? AND org_id = ?
        """,
        (rs, i) -> mapRow(rs),
        id,
        OrganizationContext.current()
    );
    return rows.stream().findFirst();
  }

  public static Attachment mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    Timestamp scanned = rs.getTimestamp("scanned_at");
    String statusRaw = rs.getString("scan_status");
    ScanStatus status;
    try {
      status = statusRaw == null ? ScanStatus.PENDING : ScanStatus.valueOf(statusRaw);
    } catch (IllegalArgumentException ex) {
      status = ScanStatus.PENDING;
    }
    return new Attachment(
        rs.getObject("id", UUID.class),
        rs.getString("filename"),
        rs.getString("content_type"),
        rs.getLong("size_bytes"),
        rs.getString("storage_key"),
        rs.getString("uploaded_by"),
        rs.getTimestamp("created_at").toInstant(),
        status,
        rs.getString("scan_engine"),
        rs.getString("scan_detail"),
        scanned == null ? null : scanned.toInstant()
    );
  }
}
