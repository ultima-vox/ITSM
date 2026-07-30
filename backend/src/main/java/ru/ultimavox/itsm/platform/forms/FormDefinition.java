package ru.ultimavox.itsm.platform.forms;
import java.util.*;
/** Renderable metadata only. Server-side validation and authorization always remain authoritative. */
public record FormDefinition(UUID id, String key, String objectKey, int version, List<Section> sections) {
 public record Section(String key, Map<String,String> labels, List<Field> fields) {}
 public record Field(String attributeKey, boolean required, Expression visibleWhen, Expression readOnlyWhen) {}
 public record Expression(String language, String source) { public Expression { if (!"cel".equals(language)) throw new IllegalArgumentException("Only CEL expressions are allowed"); } }
}
