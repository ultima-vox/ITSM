package ru.ultimavox.itsm.platform.storage;

import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository {

  Attachment save(Attachment attachment);

  Optional<Attachment> findById(UUID id);
}
