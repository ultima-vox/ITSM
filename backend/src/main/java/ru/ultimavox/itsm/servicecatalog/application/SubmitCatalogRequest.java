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
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
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
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    Instant now = Instant.now();
    Long sequence = jdbc.queryForObject("SELECT nextval('catalog_request_number_seq')", Long.class);
    String number = "CRQ-%06d".formatted(sequence);
    String payloadJson = writeJson(command.formPayload());
    ApprovalConfig approval = jdbc.queryForObject(
        "SELECT approval_required, approver_role FROM catalog_item WHERE id=?",
        (rs, row) -> new ApprovalConfig(rs.getBoolean(1), rs.getString(2)), item.id());
    String initialStatus = approval != null && approval.required() ? "PENDING_APPROVAL" : "FULFILLING";
    if (approval != null && approval.required()
        && (approval.approverRole() == null || approval.approverRole().isBlank())) {
      throw new IllegalStateException("Approval-required catalog item has no approver role");
    }

    jdbc.update(
        """
            INSERT INTO catalog_request (id, org_id, number, catalog_item_id, requester_id, status, form_payload, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?::jsonb,?,?)
            """,
        id, OrganizationContext.current(), number, item.id(), actor, initialStatus, payloadJson,
        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
    );
    if (approval != null && approval.required()) {
      jdbc.update("INSERT INTO catalog_request_approval(id,org_id,request_id,approver_role,state,created_at) VALUES (?,?,?,?,?,?)",
          UUID.randomUUID(), OrganizationContext.current(), id, approval.approverRole(), "PENDING",
          java.sql.Timestamp.from(now));
    } else {
      insertFulfillmentTask(id, item.key(), now);
    }

    Map<String, Object> state = Map.of(
        "number", number,
        "catalogItemId", item.id().toString(),
        "catalogItemKey", item.key(),
        "status", initialStatus,
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
    return new Submitted(id, number, initialStatus, item.id());
  }

  private void insertFulfillmentTask(UUID requestId, String itemKey, Instant now) {
    jdbc.update("INSERT INTO catalog_fulfillment_task(id,org_id,request_id,title,state,created_at) VALUES (?,?,?,?,?,?)",
        UUID.randomUUID(), OrganizationContext.current(), requestId, "Fulfill " + itemKey, "OPEN",
        java.sql.Timestamp.from(now));
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
  private record ApprovalConfig(boolean required, String approverRole) {}
}
