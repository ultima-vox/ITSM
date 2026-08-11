package ru.ultimavox.itsm.servicecatalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.servicecatalog.application.CatalogQuery;
import ru.ultimavox.itsm.servicecatalog.application.CatalogRequestQuery;
import ru.ultimavox.itsm.servicecatalog.application.CatalogFulfillmentService;
import ru.ultimavox.itsm.servicecatalog.application.CatalogBundleAdminService;
import ru.ultimavox.itsm.servicecatalog.application.SubmitCatalogRequest;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Service Catalog")
class CatalogController {
  private final CatalogQuery query;
  private final SubmitCatalogRequest submit;
  private final AccessControl access;
  private final CatalogRequestQuery requests;
  private final CatalogFulfillmentService fulfillment;
  private final CatalogBundleAdminService bundleAdmin;

  CatalogController(CatalogQuery query, SubmitCatalogRequest submit, CatalogRequestQuery requests,
                    CatalogFulfillmentService fulfillment, CatalogBundleAdminService bundleAdmin, AccessControl access) {
    this.query = query;
    this.submit = submit;
    this.access = access;
    this.requests = requests;
    this.fulfillment = fulfillment;
    this.bundleAdmin = bundleAdmin;
  }

  @GetMapping("/items")
  @Operation(summary = "List or search published catalog items by category")
  List<CatalogQuery.CatalogItemView> list(
      Authentication authentication,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String q,
      @RequestParam(required = false, defaultValue = "ru") String locale
  ) {
    access.require(authentication.getName(), "catalog.read", "catalog-item", null);
    return query.search(category, q, locale);
  }

  @GetMapping("/items/{id}")
  @Operation(summary = "Get catalog item detail")
  CatalogQuery.CatalogItemDetail get(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestParam(required = false, defaultValue = "ru") String locale
  ) {
    access.require(authentication.getName(), "catalog.read", "catalog-item", id.toString());
    return query.findById(id, locale)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog item not found"));
  }

  @PostMapping("/items/{id}/requests")
  @Operation(summary = "Submit a service request from a catalog item")
  ResponseEntity<SubmitCatalogRequest.Submitted> submitRequest(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody SubmitRequest body
  ) {
    access.require(authentication.getName(), "catalog.request", "catalog-item", id.toString());
    SubmitCatalogRequest.Submitted result;
    try {
      result = submit.submit(new SubmitCatalogRequest.Command(id,
          body.formPayload() == null ? Map.of() : body.formPayload(), subjectContext(authentication)),
          authentication.getName());
    } catch (AccessDeniedException ex) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
    }
    return ResponseEntity.created(URI.create("/api/v1/catalog/requests/" + result.id())).body(result);
  }

  record SubmitRequest(Map<String, Object> formPayload) {}

  private static Map<String,Object> subjectContext(Authentication authentication) {
    Map<String,Object> context = new java.util.HashMap<>();
    context.put("id", authentication.getName());
    context.put("roles", authentication.getAuthorities().stream().map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).toList());
    if (authentication.getPrincipal() instanceof Jwt jwt) {
      for (String key : List.of("department", "country", "location", "employee_type")) {
        Object value=jwt.getClaims().get(key);
        if (value instanceof String || value instanceof Number || value instanceof Boolean) context.put(key,value);
      }
    }
    return Map.copyOf(context);
  }

  @GetMapping("/requests")
  @Operation(summary = "List current requester's catalog requests")
  List<CatalogRequestQuery.RequestView> listRequests(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    access.require(authentication.getName(), "catalog.request", "catalog-request", null);
    return requests.listMine(authentication.getName(), page, size);
  }

  @GetMapping("/requests/{id}")
  @Operation(summary = "Track current requester's catalog request")
  CatalogRequestQuery.RequestView getRequest(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "catalog.request", "catalog-request", id.toString());
    return requests.findMine(id, authentication.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog request not found"));
  }

  @GetMapping("/operations/requests")
  @Operation(summary = "List tenant catalog requests for fulfillment operators")
  List<CatalogRequestQuery.RequestView> operationRequests(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size
  ) {
    access.require(authentication.getName(), "catalog.fulfill", "catalog-request", null);
    return requests.listOperations(page, size);
  }

  @GetMapping("/requests/{id}/approvals")
  List<CatalogFulfillmentService.ApprovalView> approvals(Authentication authentication, @PathVariable UUID id) {
    authorizeRequestRead(authentication.getName(), id);
    return fulfillment.approvals(id);
  }

  @PostMapping("/requests/{id}/approvals/{approvalId}/decision")
  CatalogFulfillmentService.ApprovalView decideApproval(
      Authentication authentication, @PathVariable UUID id, @PathVariable UUID approvalId,
      @Valid @RequestBody ApprovalDecisionRequest request
  ) {
    String actor = authentication.getName();
    access.require(actor, "catalog.approve", "catalog-request", id.toString());
    return fulfillment.decide(id, approvalId, request.decision(), request.comment(), actor);
  }

  @GetMapping("/requests/{id}/tasks")
  List<CatalogFulfillmentService.TaskView> tasks(Authentication authentication, @PathVariable UUID id) {
    authorizeRequestRead(authentication.getName(), id);
    return fulfillment.tasks(id);
  }

  @PostMapping("/requests/{id}/tasks/{taskId}")
  CatalogFulfillmentService.TaskView updateTask(
      Authentication authentication, @PathVariable UUID id, @PathVariable UUID taskId,
      @Valid @RequestBody TaskUpdateRequest request
  ) {
    String actor = authentication.getName();
    access.require(actor, "catalog.fulfill", "catalog-request", id.toString());
    return fulfillment.updateTask(id, taskId, request.state(), request.assigneeId(), actor);
  }

  private void authorizeRequestRead(String actor, UUID id) {
    if (access.isAllowed(actor, "catalog.fulfill", "catalog-request", id.toString())) return;
    access.require(actor, "catalog.request", "catalog-request", id.toString());
    if (requests.findMine(id, actor).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog request not found");
    }
  }

  record ApprovalDecisionRequest(
      @jakarta.validation.constraints.NotNull CatalogFulfillmentService.Decision decision,
      @jakarta.validation.constraints.Size(max=2000) String comment
  ) {}
  record TaskUpdateRequest(
      @jakarta.validation.constraints.NotNull CatalogFulfillmentService.TaskState state,
      @jakarta.validation.constraints.Size(max=128) String assigneeId
  ) {}

  @org.springframework.web.bind.annotation.PutMapping("/items/{id}/bundle")
  List<CatalogBundleAdminService.Component> replaceBundle(Authentication authentication, @PathVariable UUID id,
      @Valid @RequestBody BundleRequest request) {
    String actor=authentication.getName();
    access.require(actor,"catalog.admin","catalog-item",id.toString());
    return bundleAdmin.replace(id,request.components(),actor);
  }
  record BundleRequest(@jakarta.validation.constraints.NotNull List<CatalogBundleAdminService.Component> components) {}
}
