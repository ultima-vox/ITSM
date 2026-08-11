package ru.ultimavox.itsm.platform.storage;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface AttachmentRepository {

  Attachment save(Attachment attachment);

  Optional<Attachment> findById(UUID id);

  boolean canRead(UUID id, String subjectId);

  void grantRead(UUID id, String subjectId, String sourceType, String sourceId,
                 String grantedBy, Instant createdAt);

  void revokeSource(UUID id, String subjectId, String sourceType, String sourceId);
}
