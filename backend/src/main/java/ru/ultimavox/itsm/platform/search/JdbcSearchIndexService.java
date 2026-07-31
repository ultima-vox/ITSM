package ru.ultimavox.itsm.platform.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JDBC-backed search projection stub. Active when OpenSearch is not configured
 * ({@code itsm.opensearch.url} / {@code OPENSEARCH_URL} blank). Scope filtering at query time.
 */
@Service
@Conditional(OpenSearchDisabledCondition.class)
public class JdbcSearchIndexService implements SearchIndexService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcSearchIndexService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void index(SearchDocument document) {
        try {
            jdbc.update(
                    """
                    INSERT INTO search_document (id, object_type, title, body, scopes, facets, updated_at)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                    ON CONFLICT (id) DO UPDATE SET
                      object_type = EXCLUDED.object_type,
                      title = EXCLUDED.title,
                      body = EXCLUDED.body,
                      scopes = EXCLUDED.scopes,
                      facets = EXCLUDED.facets,
                      updated_at = EXCLUDED.updated_at
                    """,
                    document.id(),
                    document.objectType(),
                    document.title(),
                    document.body() == null ? "" : document.body(),
                    json.writeValueAsString(document.scopes()),
                    json.writeValueAsString(document.facets()),
                    Timestamp.from(document.updatedAt() != null ? document.updatedAt() : Instant.now())
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize search document", ex);
        }
    }

    @Override
    @Transactional
    public void delete(String id) {
        jdbc.update("DELETE FROM search_document WHERE id = ?", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchDocument> search(String query, Set<String> allowedScopes, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        String pattern = "%" + (query == null ? "" : query.trim()) + "%";
        List<SearchDocument> rows = jdbc.query(
                """
                SELECT id, object_type, title, body, scopes::text, facets::text, updated_at
                FROM search_document
                WHERE title ILIKE ? OR body ILIKE ?
                ORDER BY updated_at DESC
                LIMIT ?
                """,
                (rs, i) -> new SearchDocument(
                        rs.getString("id"),
                        rs.getString("object_type"),
                        rs.getString("title"),
                        rs.getString("body"),
                        readStringSet(rs.getString("scopes")),
                        rs.getTimestamp("updated_at").toInstant(),
                        readObjectMap(rs.getString("facets"))
                ),
                pattern, pattern, safeLimit
        );

        if (allowedScopes == null || allowedScopes.isEmpty()) {
            return rows;
        }
        List<SearchDocument> filtered = new ArrayList<>();
        for (SearchDocument doc : rows) {
            if (doc.scopes().isEmpty() || doc.scopes().stream().anyMatch(allowedScopes::contains)) {
                filtered.add(doc);
            }
        }
        return filtered;
    }

    private Set<String> readStringSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        try {
            return json.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return Set.of();
        }
    }

    private Map<String, Object> readObjectMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }
}
