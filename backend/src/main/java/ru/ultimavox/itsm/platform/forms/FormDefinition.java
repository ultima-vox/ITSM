package ru.ultimavox.itsm.platform.forms;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renderable form metadata only. Server-side validation and authorization always remain authoritative.
 * Conditional expressions are CEL-only — no executable scripts.
 */
public record FormDefinition(
        UUID id,
        String key,
        String objectKey,
        int version,
        List<Section> sections
) {
    public FormDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Form key is required");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Form objectKey is required");
        }
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    public record Section(String key, Map<String, String> labels, List<Field> fields) {
        public Section {
            labels = labels == null ? Map.of() : Map.copyOf(labels);
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public record Field(
            String attributeKey,
            boolean required,
            Expression visibleWhen,
            Expression readOnlyWhen
    ) {}

    public record Expression(String language, String source) {
        public Expression {
            if (!"cel".equals(language)) {
                throw new IllegalArgumentException("Only CEL expressions are allowed");
            }
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("Expression source is required");
            }
        }
    }
}
