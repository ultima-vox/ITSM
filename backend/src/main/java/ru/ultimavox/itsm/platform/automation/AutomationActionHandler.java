package ru.ultimavox.itsm.platform.automation;

import java.util.Map;
import ru.ultimavox.itsm.platform.event.DomainEvent;

/**
 * Extension point for allowlisted business actions. Implementations live in the module that
 * owns the capability (e.g. service desk assignment) and are invoked transactionally by the
 * {@link AutomationRunner}. Action types are validated against registered handlers at rule
 * save time and executed only when allowlisted.
 */
public interface AutomationActionHandler {

  /** Stable action type used in rules, e.g. {@code assign}. Must match the allowlist contract. */
  String actionType();

  /**
   * Executes the action for the triggering event.
   *
   * @throws RuntimeException on failure; the runner records the action as FAILED and continues
   */
  void execute(DomainEvent event, Map<String, Object> parameters);
}
