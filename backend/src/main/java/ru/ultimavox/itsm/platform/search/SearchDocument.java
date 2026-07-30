package ru.ultimavox.itsm.platform.search;
import java.time.Instant; import java.util.*;
/** Authorization metadata travels with the projection and is rechecked at query time. */
public record SearchDocument(String id, String objectType, String title, String body, Set<String> scopes, Instant updatedAt, Map<String,Object> facets) {}
