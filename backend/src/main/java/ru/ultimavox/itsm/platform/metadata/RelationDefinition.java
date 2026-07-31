package ru.ultimavox.itsm.platform.metadata;

import java.util.Map;

/** Declared relation between object types; cardinality is enforced by consuming modules. */
public record RelationDefinition(
        String key,
        String targetObjectKey,
        Cardinality cardinality,
        boolean required,
        Map<String, String> labels
) {
    public RelationDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Relation key is required");
        }
        if (targetObjectKey == null || targetObjectKey.isBlank()) {
            throw new IllegalArgumentException("Relation targetObjectKey is required");
        }
        if (cardinality == null) {
            throw new IllegalArgumentException("Relation cardinality is required");
        }
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }

    public enum Cardinality {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY
    }
}
