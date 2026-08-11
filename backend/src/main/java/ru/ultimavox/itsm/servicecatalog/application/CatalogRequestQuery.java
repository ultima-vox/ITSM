package ru.ultimavox.itsm.servicecatalog.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

/** Requester-scoped catalog request tracking; never exposes another principal's payload. */
@Service
public class CatalogRequestQuery {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  CatalogRequestQuery(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<RequestView> listMine(String requesterId, int page, int size) {
    int boundedSize = Math.min(Math.max(size, 1), 100);
    int offset = Math.max(page, 0) * boundedSize;
    return jdbc.query(BASE_SELECT + " WHERE cr.org_id=? AND cr.requester_id=? ORDER BY cr.updated_at DESC LIMIT ? OFFSET ?",
        (rs, row) -> map(rs), OrganizationContext.current(), requesterId, boundedSize, offset);
  }

  public Optional<RequestView> findMine(UUID id, String requesterId) {
    return jdbc.query(BASE_SELECT + " WHERE cr.id=? AND cr.org_id=? AND cr.requester_id=?",
        (rs, row) -> map(rs), id, OrganizationContext.current(), requesterId).stream().findFirst();
  }

  public List<RequestView> listOperations(int page, int size) {
    int boundedSize = Math.min(Math.max(size, 1), 100);
    int offset = Math.max(page, 0) * boundedSize;
    return jdbc.query(BASE_SELECT + " WHERE cr.org_id=? ORDER BY cr.updated_at DESC LIMIT ? OFFSET ?",
        (rs, row) -> map(rs), OrganizationContext.current(), boundedSize, offset);
  }

  private RequestView map(java.sql.ResultSet rs) throws java.sql.SQLException {
    Map<String, Object> payload;
    try {
      payload = json.readValue(rs.getString("form_payload"), new TypeReference<>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("Invalid catalog request payload", ex);
    }
    return new RequestView(rs.getObject("id", UUID.class), rs.getString("number"),
        rs.getObject("catalog_item_id", UUID.class), rs.getString("item_key"), rs.getString("status"),
        payload, instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));
  }

  private static Instant instant(Timestamp value) { return value.toInstant(); }

  private static final String BASE_SELECT = """
      SELECT cr.id, cr.number, cr.catalog_item_id, ci.item_key, cr.status,
             cr.form_payload::text AS form_payload, cr.created_at, cr.updated_at
      FROM catalog_request cr JOIN catalog_item ci ON ci.id=cr.catalog_item_id
      """;

  public record RequestView(UUID id, String number, UUID catalogItemId, String catalogItemKey,
                            String status, Map<String, Object> formPayload,
                            Instant createdAt, Instant updatedAt) {}
}
