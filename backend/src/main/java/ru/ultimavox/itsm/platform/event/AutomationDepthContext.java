package ru.ultimavox.itsm.platform.event;

import java.util.function.Supplier;

/**
 * Thread-local automation execution depth. The outbox dispatcher reads the current depth when
 * recording a domain event; the automation listener uses it to bound cascades and prevent
 * infinite automation loops (rule A &rarr; event &rarr; rule B &rarr; event &rarr; rule A &hellip;).
 *
 * <p>The depth travels only in-process (never persisted into the outbox payload). External
 * consumers replay events independently and are guarded by the idempotent action log.
 */
public final class AutomationDepthContext {

  /**
   * Maximum number of sequential automation steps an event may trigger. Events produced at
   * this depth are ignored by the automation listener; the originating event still completes.
   */
  public static final int MAX_AUTOMATION_DEPTH = 6;

  private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

  private AutomationDepthContext() {}

  /** Current automation depth for this thread; 0 outside automation execution. */
  public static int current() {
    Integer depth = DEPTH.get();
    return depth == null ? 0 : depth;
  }

  /** Runs {@code action} while the automation depth is set to {@code depth}, restoring afterwards. */
  public static <T> T atDepth(int depth, Supplier<T> action) {
    Integer previous = DEPTH.get();
    DEPTH.set(depth);
    try {
      return action.get();
    } finally {
      if (previous == null) {
        DEPTH.remove();
      } else {
        DEPTH.set(previous);
      }
    }
  }
}
