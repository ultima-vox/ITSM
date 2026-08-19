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

  @Override
  public boolean canRead(UUID id, String subjectId) {
    Integer count = jdbc.queryForObject(
        "SELECT count(*) FROM attachment_access_grant WHERE attachment_id=? AND org_id=? AND subject_id=?",
        Integer.class, id, OrganizationContext.current(), subjectId);
    return count != null && count > 0;
  }

  @Override
  public void grantRead(UUID id, String subjectId, String sourceType, String sourceId,
                        String grantedBy, Instant createdAt) {
    if (subjectId == null || subjectId.isBlank()) return;
    jdbc.update(
        """
        INSERT INTO attachment_access_grant(
          attachment_id,org_id,subject_id,source_type,source_id,granted_by,created_at
        ) VALUES(?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
        """,
        id, OrganizationContext.current(), subjectId, sourceType, sourceId, grantedBy,
        Timestamp.from(createdAt));
  }

  @Override
  public void revokeSource(UUID id, String subjectId, String sourceType, String sourceId) {
    jdbc.update(
        "DELETE FROM attachment_access_grant WHERE attachment_id=? AND org_id=? AND subject_id=? AND source_type=? AND source_id=?",
        id, OrganizationContext.current(), subjectId, sourceType, sourceId);
  }

  @Override
  public void updateScan(UUID id, ScanStatus status, String engine, String detail, Instant scannedAt) {
    jdbc.update(
        """
        UPDATE attachment
        SET scan_status = ?, scan_engine = ?, scan_detail = ?, scanned_at = ?
        WHERE id = ? AND org_id = ?
        """,
        status.name(),
        engine,
        detail,
        scannedAt == null ? null : Timestamp.from(scannedAt),
        id,
        OrganizationContext.current()
    );
  }

  @Override
  public List<Attachment> listUnscanned(int limit) {
    return jdbc.query(
        """
        SELECT id, filename, content_type, size_bytes, storage_key, uploaded_by, created_at,
               scan_status, scan_engine, scan_detail, scanned_at
        FROM attachment
        WHERE org_id = ? AND scan_status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT ?
        """,
        (rs, i) -> mapRow(rs),
        OrganizationContext.current(),
        Math.max(1, limit)
    );
  }

  @Override
  public List<String> distinctOrgIdsWithUnscanned() {
    return jdbc.query(
        "SELECT DISTINCT org_id FROM attachment WHERE scan_status = 'PENDING'",
        (rs, i) -> rs.getString(1)
    );
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
