package ru.ultimavox.itsm.platform.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for in-app notifications. */
public interface NotificationStore {

  /**
   * Insert a notification. When {@code dedupeKey} is set and a row already exists
   * for the same recipient, the existing row is returned (no duplicate).
   */
  StoredNotification save(StoredNotification notification);

  List<StoredNotification> listForRecipient(
      String recipientSubject,
      int limit,
      int offset,
      boolean unreadOnly
  );

  /** Newest-first, capped list (backward-compatible helper). */
  default List<StoredNotification> listForRecipient(String recipientSubject, int limit) {
    return listForRecipient(recipientSubject, limit, 0, false);
  }

  Optional<StoredNotification> findById(UUID id);

  /** Mark one notification read for the recipient. Returns true if a row was updated. */
  boolean markRead(UUID id, String recipientSubject, Instant readAt);

  /** Mark all unread for recipient. Returns rows updated. */
  int markAllRead(String recipientSubject, Instant readAt);

  long countUnread(String recipientSubject);

  /** Retention: delete rows older than cutoff. Returns deleted count. */
  int deleteOlderThan(Instant cutoff);
}
