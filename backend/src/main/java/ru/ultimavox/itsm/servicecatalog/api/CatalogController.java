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
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.servicecatalog.application.CatalogQuery;
import ru.ultimavox.itsm.servicecatalog.application.CatalogRequestQuery;
import ru.ultimavox.itsm.servicecatalog.application.SubmitCatalogRequest;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Service Catalog")
class CatalogController {
  private final CatalogQuery query;
  private final SubmitCatalogRequest submit;
  private final AccessControl access;
  private final CatalogRequestQuery requests;

  CatalogController(CatalogQuery query, SubmitCatalogRequest submit, CatalogRequestQuery requests, AccessControl access) {
    this.query = query;
    this.submit = submit;
    this.access = access;
    this.requests = requests;
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
    var result = submit.submit(
        new SubmitCatalogRequest.Command(id, body.formPayload() == null ? Map.of() : body.formPayload()),
        authentication.getName()
    );
    return ResponseEntity.created(URI.create("/api/v1/catalog/requests/" + result.id())).body(result);
  }

  record SubmitRequest(Map<String, Object> formPayload) {}

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
}
