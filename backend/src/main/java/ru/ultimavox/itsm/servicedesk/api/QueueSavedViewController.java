package ru.ultimavox.itsm.servicedesk.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.authorization.SelfScopedEndpoint;
import ru.ultimavox.itsm.servicedesk.application.QueueSavedViewService;
import ru.ultimavox.itsm.servicedesk.application.QueueSavedViewService.SavedView;

@RestController
@RequestMapping("/api/v1/me/queue-views")
@SelfScopedEndpoint
@Tag(name = "Service Desk Saved views")
class QueueSavedViewController {
  private final QueueSavedViewService views;

  QueueSavedViewController(QueueSavedViewService views) {
    this.views = views;
  }

  @GetMapping
  @Operation(summary = "List saved queue views for the current operator", operationId = "listQueueSavedViews")
  List<SavedView> list(Authentication authentication) {
    return views.list(authentication.getName());
  }

  @PostMapping
  @Operation(summary = "Save the current queue filters as a named view", operationId = "createQueueSavedView")
  ResponseEntity<SavedView> create(Authentication authentication, @Valid @RequestBody CreateQueueViewRequest body) {
    try {
      SavedView created = views.create(
          authentication.getName(),
          new QueueSavedViewService.Command(
              body.name(), body.tab(), body.priority(), body.type(), body.status(), body.sla()));
      return ResponseEntity.status(HttpStatus.CREATED).body(created);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (QueueSavedViewService.DuplicateNameException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a saved queue view owned by the current operator", operationId = "deleteQueueSavedView")
  void delete(Authentication authentication, @PathVariable UUID id) {
    try {
      views.delete(authentication.getName(), id);
    } catch (QueueSavedViewService.NotFoundException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  record CreateQueueViewRequest(
      @NotBlank @Size(max = 80) String name,
      @Size(max = 32) String tab,
      @Size(max = 16) String priority,
      @Size(max = 32) String type,
      @Size(max = 32) String status,
      @Size(max = 32) String sla
  ) {}
}
