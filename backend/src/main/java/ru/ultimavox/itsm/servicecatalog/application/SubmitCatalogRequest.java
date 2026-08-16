package ru.ultimavox.itsm.servicecatalog.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicecatalog.domain.CatalogItem;

@Service
public class SubmitCatalogRequest {
  private final JdbcTemplate jdbc;
  private final CatalogQuery catalogQuery;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final ObjectMapper json;

  public SubmitCatalogRequest(
      JdbcTemplate jdbc,
      CatalogQuery catalogQuery,
      AuditTrail audit,
      IntegrationEventOutbox outbox,
      ObjectMapper json
  ) {
    this.jdbc = jdbc;
    this.catalogQuery = catalogQuery;
    this.audit = audit;
    this.outbox = outbox;
    this.json = json;
  }

  @Transactional
  public Submitted submit(Command command, String actor) {
    CatalogItem item = catalogQuery.findDomainPublished(command.catalogItemId())
        .orElseThrow(() -> new IllegalArgumentException("Catalog item not found: " + command.catalogItemId()));
    if (item.status() != CatalogItem.Status.PUBLISHED) {
      throw new IllegalStateException("Only published catalog items accept requests");
    }

    UUID id = UUID.randomUUID();
    UUID correlationId = UUID.randomUUID();
    Instant now = Instant.now();
    Long sequence = jdbc.queryForObject("SELECT nextval('catalog_request_number_seq')", Long.class);
    String number = "CRQ-%06d".formatted(sequence);
    String payloadJson = writeJson(command.formPayload());

    jdbc.update(
        """
            INSERT INTO catalog_request (id, number, catalog_item_id, requester_id, status, form_payload, created_at, updated_at)
            VALUES (?,?,?,?,?,?::jsonb,?,?)
            """,
        id, number, item.id(), actor, "SUBMITTED", payloadJson,
        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
    );

    Map<String, Object> state = Map.of(
        "number", number,
        "catalogItemId", item.id().toString(),
        "catalogItemKey", item.key(),
        "status", "SUBMITTED",
        "requesterId", actor
    );
    audit.append(new AuditTrail.Entry(
        actor, "catalog-request.submitted", "catalog-request", id.toString(),
        Map.of(), state, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "catalog-request.submitted", 1, now, correlationId,
        "catalog-request", id.toString(), state
    ));
    return new Submitted(id, number, "SUBMITTED", item.id());
  }

  private String writeJson(Map<String, Object> payload) {
    try {
      return json.writeValueAsString(payload == null ? Map.of() : payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize form payload", ex);
    }
  }

  public record Command(UUID catalogItemId, Map<String, Object> formPayload) {}

  public record Submitted(UUID id, String number, String status, UUID catalogItemId) {}
}
