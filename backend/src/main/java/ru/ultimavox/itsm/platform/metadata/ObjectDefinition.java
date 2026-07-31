package ru.ultimavox.itsm.platform.metadata;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned, tenant-scoped metadata definition. Business modules consume this contract
 * rather than hard-code presentation fields.
 */
public record ObjectDefinition(
        UUID id,
        String key,
        int version,
        Map<String, String> labels,
        Map<String, AttributeDefinition> attributes,
        List<RelationDefinition> relations
) {
    public ObjectDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Object key is required");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Object version must be >= 1");
        }
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        relations = relations == null ? List.of() : List.copyOf(relations);
    }

    /** Convenience: attributes as ordered list. */
    public List<AttributeDefinition> attributeList() {
        return List.copyOf(attributes.values());
    }

    /**
     * Backward-compatible field view used by older call sites.
     * Prefer {@link #attributes()}.
     */
    public Map<String, FieldDefinition> fields() {
        return attributes.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> new FieldDefinition(
                                e.getValue().key(),
                                toFieldType(e.getValue().type()),
                                e.getValue().required(),
                                e.getValue().searchable(),
                                e.getValue().labels()
                        )
                ));
    }

    public record FieldDefinition(
            String key,
            FieldType type,
            boolean required,
            boolean searchable,
            Map<String, String> labels
    ) {}

    public enum FieldType {
        TEXT, RICH_TEXT, NUMBER, DATE_TIME, USER, REFERENCE, ENUM, BOOLEAN, ATTACHMENT
    }

    private static FieldType toFieldType(AttributeDefinition.AttributeType type) {
        return FieldType.valueOf(type.name());
    }
}
