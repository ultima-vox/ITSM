package ru.ultimavox.itsm.platform.notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory notification store for unit tests and isolated local demos.
 * Not a Spring bean — production uses {@link JdbcNotificationStore}.
 */
public class InMemoryNotificationStore implements NotificationStore {

  static final int RETAIN_LIMIT = 500;

  private final CopyOnWriteArrayList<StoredNotification> rows = new CopyOnWriteArrayList<>();

  @Override
  public StoredNotification save(StoredNotification notification) {
    Objects.requireNonNull(notification, "notification");
    if (notification.dedupeKey() != null) {
      Optional<StoredNotification> existing =
          findByDedupe(notification.recipientSubject(), notification.dedupeKey());
      if (existing.isPresent()) {
        return existing.get();
      }
    }
    rows.add(notification);
    while (rows.size() > RETAIN_LIMIT) {
      rows.remove(0);
    }
    return notification;
  }

  @Override
  public List<StoredNotification> listForRecipient(
      String recipientSubject,
      int limit,
      int offset,
      boolean unreadOnly
  ) {
    if (recipientSubject == null || recipientSubject.isBlank() || limit <= 0) {
      return List.of();
    }
    int cap = Math.min(limit, 100);
    int off = Math.max(offset, 0);
    List<StoredNotification> matched = new ArrayList<>();
    for (StoredNotification n : rows) {
      if (!recipientSubject.equals(n.recipientSubject())) {
        continue;
      }
      if (unreadOnly && n.readAt() != null) {
        continue;
      }
      matched.add(n);
    }
    matched.sort(Comparator.comparing(StoredNotification::createdAt).reversed());
    if (off >= matched.size()) {
      return List.of();
    }
    int end = Math.min(off + cap, matched.size());
    return List.copyOf(matched.subList(off, end));
  }

  @Override
  public Optional<StoredNotification> findById(UUID id) {
    return rows.stream().filter(n -> n.id().equals(id)).findFirst();
  }

  @Override
  public boolean markRead(UUID id, String recipientSubject, Instant readAt) {
    for (int i = 0; i < rows.size(); i++) {
      StoredNotification n = rows.get(i);
      if (n.id().equals(id) && recipientSubject.equals(n.recipientSubject()) && n.readAt() == null) {
        rows.set(i, withReadAt(n, readAt));
        return true;
      }
    }
    return false;
  }

  @Override
  public int markAllRead(String recipientSubject, Instant readAt) {
    int count = 0;
    for (int i = 0; i < rows.size(); i++) {
      StoredNotification n = rows.get(i);
      if (recipientSubject.equals(n.recipientSubject()) && n.readAt() == null) {
        rows.set(i, withReadAt(n, readAt));
        count++;
      }
    }
    return count;
  }

  @Override
  public long countUnread(String recipientSubject) {
    return rows.stream()
        .filter(n -> recipientSubject.equals(n.recipientSubject()) && n.readAt() == null)
        .count();
  }

  @Override
  public int deleteOlderThan(Instant cutoff) {
    int before = rows.size();
    rows.removeIf(n -> n.createdAt().isBefore(cutoff));
    return before - rows.size();
  }

  /** Test/demo helper. */
  int size() {
    return rows.size();
  }

  void clear() {
    rows.clear();
  }

  private Optional<StoredNotification> findByDedupe(String recipient, String dedupeKey) {
    return rows.stream()
        .filter(n -> recipient.equals(n.recipientSubject()) && dedupeKey.equals(n.dedupeKey()))
        .findFirst();
  }

  private static StoredNotification withReadAt(StoredNotification n, Instant readAt) {
    return new StoredNotification(
        n.id(),
        n.createdAt(),
        n.correlationId(),
        n.templateKey(),
        n.recipientSubject(),
        n.locale(),
        n.variables(),
        n.channel(),
        readAt,
        n.source(),
        n.entityType(),
        n.entityId(),
        n.dedupeKey()
    );
  }
}
