package ru.ultimavox.itsm.platform.metadata;

import java.util.List;
import java.util.Map;

/**
 * Typed attribute of an {@link ObjectDefinition}. Value types mirror Naumen-style metadata fields.
 */
public record AttributeDefinition(
        String key,
        AttributeType type,
        boolean required,
        boolean searchable,
        Map<String, String> labels,
        List<String> enumValues
) {
    public AttributeDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Attribute key is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Attribute type is required");
        }
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        if (type == AttributeType.ENUM && enumValues.isEmpty()) {
            throw new IllegalArgumentException("ENUM attribute '%s' requires enumValues".formatted(key));
        }
    }

    public enum AttributeType {
        TEXT,
        RICH_TEXT,
        NUMBER,
        DATE_TIME,
        USER,
        REFERENCE,
        ENUM,
        BOOLEAN,
        ATTACHMENT
    }
}
