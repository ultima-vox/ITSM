package ru.ultimavox.itsm.platform.search;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Authorization metadata travels with the projection and is rechecked at query time. */
public record SearchDocument(
        String id,
        String objectType,
        String title,
        String body,
        Set<String> scopes,
        Instant updatedAt,
        Map<String, Object> facets
) {
    public SearchDocument {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        facets = facets == null ? Map.of() : Map.copyOf(facets);
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }
}
