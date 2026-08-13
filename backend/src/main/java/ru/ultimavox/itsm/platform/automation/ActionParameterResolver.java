package ru.ultimavox.itsm.platform.automation;

import java.util.Map;
import ru.ultimavox.itsm.platform.event.DomainEvent;

/**
 * Resolves action parameters to concrete values. A literal value is used as-is; a
 * {@code {{data.field}}} or {@code {{event.field}}} placeholder is resolved from the triggering
 * event so rules can route to the event's own payload (e.g. assign to the requester).
 */
public final class ActionParameterResolver {

  private ActionParameterResolver() {}

  public static String resolve(Map<String, Object> parameters, DomainEvent event, String key) {
    Object raw = parameters.get(key);
    if (raw == null) return null;
    String value = String.valueOf(raw).trim();
    if (value.length() >= 4 && value.startsWith("{{") && value.endsWith("}}")) {
      return resolvePlaceholder(value.substring(2, value.length() - 2).trim(), event);
    }
    return value;
  }

  private static String resolvePlaceholder(String path, DomainEvent event) {
    if (path.startsWith("data.")) {
      Object value = event.data().get(path.substring("data.".length()));
      return value == null ? null : String.valueOf(value);
    }
    return switch (path) {
      case "event.actorId" -> event.actorId();
      case "event.organizationId" -> event.organizationId();
      case "event.aggregateId" -> event.aggregateId();
      case "event.type" -> event.type();
      default -> null;
    };
  }
}
