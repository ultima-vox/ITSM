package ru.ultimavox.itsm.platform.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Component;

/**
 * Demo notification log backed by a concurrent queue (no JDBC).
 * Cap retained rows so long-running demos do not grow unbounded.
 */
@Component
public class InMemoryNotificationStore {

  static final int RETAIN_LIMIT = 500;

  private final ConcurrentLinkedQueue<StoredNotification> queue = new ConcurrentLinkedQueue<>();

  public void add(StoredNotification notification) {
    Objects.requireNonNull(notification, "notification");
    queue.add(notification);
    while (queue.size() > RETAIN_LIMIT) {
      queue.poll();
    }
  }

  /** Newest-first list of notifications for a recipient, capped at {@code limit}. */
  public List<StoredNotification> listForRecipient(String recipientSubject, int limit) {
    if (recipientSubject == null || recipientSubject.isBlank() || limit <= 0) {
      return List.of();
    }
    int cap = Math.min(limit, 50);
    List<StoredNotification> matched = new ArrayList<>();
    for (StoredNotification n : queue) {
      if (recipientSubject.equals(n.recipientSubject())) {
        matched.add(n);
      }
    }
    Collections.reverse(matched);
    if (matched.size() <= cap) {
      return List.copyOf(matched);
    }
    return List.copyOf(matched.subList(0, cap));
  }

  /** Test/demo helper. */
  int size() {
    return queue.size();
  }

  void clear() {
    queue.clear();
  }
}
