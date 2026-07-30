package ru.ultimavox.itsm.platform.metadata;
import java.util.*;
/** Versioned, tenant-scoped metadata definition. Business modules consume this contract rather than hard-code presentation fields. */
public record ObjectDefinition(UUID id, String key, int version, Map<String, FieldDefinition> fields) {
  public record FieldDefinition(String key, FieldType type, boolean required, boolean searchable, Map<String, String> labels) {}
  public enum FieldType { TEXT, RICH_TEXT, NUMBER, DATE_TIME, USER, REFERENCE, ENUM, BOOLEAN, ATTACHMENT }
}
