package ru.ultimavox.itsm.servicedesk.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.servicedesk.api.WorkItemResponses.ActivityResponse;
import ru.ultimavox.itsm.servicedesk.api.WorkItemResponses.AttachmentLinkResponse;
import ru.ultimavox.itsm.servicedesk.api.WorkItemResponses.CommentResponse;
import ru.ultimavox.itsm.servicedesk.api.WorkItemResponses.StatsResponse;
import ru.ultimavox.itsm.servicedesk.api.WorkItemResponses.WorkItemPageResponse;
import ru.ultimavox.itsm.servicedesk.api.WorkItemResponses.WorkItemResponse;
import ru.ultimavox.itsm.servicedesk.application.AddWorkItemComment;
import ru.ultimavox.itsm.servicedesk.application.AssignWorkItem;
import ru.ultimavox.itsm.servicedesk.application.CreateWorkItem;
import ru.ultimavox.itsm.servicedesk.application.GetWorkItem;
import ru.ultimavox.itsm.servicedesk.application.ListWorkItemComments;
import ru.ultimavox.itsm.servicedesk.application.TransitionWorkItem;
import ru.ultimavox.itsm.servicedesk.application.UpdateWorkItem;
import ru.ultimavox.itsm.servicedesk.application.WorkItemActivityQuery;
import ru.ultimavox.itsm.servicedesk.application.WorkItemAttachmentService;
import ru.ultimavox.itsm.servicedesk.application.WorkItemQuery;
import ru.ultimavox.itsm.servicedesk.application.WorkItemStatsQuery;
import ru.ultimavox.itsm.servicedesk.application.WorkItemWatcherService;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

@RestController
@RequestMapping("/api/v1/work-items")
@Tag(name = "Service Desk — Work Items")
class WorkItemController {

  private final CreateWorkItem createWorkItem;
  private final WorkItemQuery workItemQuery;
  private final GetWorkItem getWorkItem;
  private final UpdateWorkItem updateWorkItem;
  private final AssignWorkItem assignWorkItem;
  private final TransitionWorkItem transitionWorkItem;
  private final AddWorkItemComment addWorkItemComment;
  private final ListWorkItemComments listWorkItemComments;
  private final WorkItemActivityQuery activityQuery;
  private final WorkItemStatsQuery statsQuery;
  private final WorkItemAttachmentService workItemAttachments;
  private final WorkItemWatcherService watchers;
  private final AccessControl access;

  WorkItemController(
      CreateWorkItem createWorkItem,
      WorkItemQuery workItemQuery,
      GetWorkItem getWorkItem,
      UpdateWorkItem updateWorkItem,
      AssignWorkItem assignWorkItem,
      TransitionWorkItem transitionWorkItem,
      AddWorkItemComment addWorkItemComment,
      ListWorkItemComments listWorkItemComments,
      WorkItemActivityQuery activityQuery,
      WorkItemStatsQuery statsQuery,
      WorkItemAttachmentService workItemAttachments,
      WorkItemWatcherService watchers,
      AccessControl access
  ) {
    this.createWorkItem = createWorkItem;
    this.workItemQuery = workItemQuery;
    this.getWorkItem = getWorkItem;
    this.updateWorkItem = updateWorkItem;
    this.assignWorkItem = assignWorkItem;
    this.transitionWorkItem = transitionWorkItem;
    this.addWorkItemComment = addWorkItemComment;
    this.listWorkItemComments = listWorkItemComments;
    this.activityQuery = activityQuery;
    this.statsQuery = statsQuery;
    this.workItemAttachments = workItemAttachments;
    this.watchers = watchers;
    this.access = access;
  }

  @PostMapping
  @Operation(summary = "Create incident or service request")
  ResponseEntity<CreateWorkItem.Created> create(
      @Valid @RequestBody CreateRequest request,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.create", "work-item", null);
    CreateWorkItem.Created created = createWorkItem.create(
        new CreateWorkItem.Command(
            request.type(),
            request.title(),
            request.description(),
            request.service(),
            request.impact(),
            request.urgency(),
            request.assigneeId(),
            request.teamId()
        ),
        actor
    );
    return ResponseEntity.created(URI.create("/api/v1/work-items/" + created.id())).body(created);
  }

  @GetMapping
  @Operation(summary = "List work items with operator filters")
  WorkItemPageResponse list(
      @RequestParam(required = false) State state,
      @RequestParam(required = false) Type type,
      @RequestParam(required = false) String assigneeId,
      @RequestParam(required = false) Priority priority,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication
  ) {
    access.require(authentication.getName(), "work-item.read", "work-item", null);
    return WorkItemPageResponse.from(
        workItemQuery.search(new WorkItemQuery.Filter(state, type, assigneeId, priority, q), page, size)
    );
  }

  @GetMapping("/stats")
  @Operation(summary = "Operator dashboard counters")
  StatsResponse stats(Authentication authentication) {
    access.require(authentication.getName(), "work-item.read", "work-item", null);
    return StatsResponse.from(statsQuery.stats());
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get work item by id")
  WorkItemResponse get(@PathVariable UUID id, Authentication authentication) {
    access.require(authentication.getName(), "work-item.read", "work-item", id.toString());
    return WorkItemResponse.from(getWorkItem.get(id));
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update work item fields")
  WorkItemResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateRequest request,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.update", "work-item", id.toString());
    return WorkItemResponse.from(updateWorkItem.update(
        id,
        new UpdateWorkItem.Command(
            request.title(),
            request.description(),
            request.service(),
            request.impact(),
            request.urgency()
        ),
        actor
    ));
  }

  @PostMapping("/{id}/assign")
  @Operation(summary = "Assign work item to operator/team")
  WorkItemResponse assign(
      @PathVariable UUID id,
      @Valid @RequestBody AssignRequest request,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.assign", "work-item", id.toString());
    return WorkItemResponse.from(assignWorkItem.assign(
        id,
        new AssignWorkItem.Command(request.assigneeId(), request.teamId()),
        actor
    ));
  }

  @PostMapping("/{id}/transitions")
  @Operation(summary = "Transition work item state")
  WorkItemResponse transition(
      @PathVariable UUID id,
      @Valid @RequestBody TransitionRequest request,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.transition", "work-item", id.toString());
    return WorkItemResponse.from(transitionWorkItem.transition(
        id,
        new TransitionWorkItem.Command(
            request.targetState(),
            request.resolutionCode(),
            request.resolutionNotes()
        ),
        actor
    ));
  }

  @GetMapping("/{id}/comments")
  @Operation(summary = "List comments on a work item")
  List<CommentResponse> listComments(@PathVariable UUID id, Authentication authentication) {
    access.require(authentication.getName(), "work-item.read", "work-item", id.toString());
    return listWorkItemComments.list(id).stream().map(CommentResponse::from).toList();
  }

  @PostMapping("/{id}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add a comment to a work item")
  CommentResponse addComment(
      @PathVariable UUID id,
      @Valid @RequestBody CommentRequest request,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.comment", "work-item", id.toString());
    return CommentResponse.from(
        addWorkItemComment.add(id, new AddWorkItemComment.Command(request.body()), actor)
    );
  }

  @GetMapping("/{id}/activity")
  @Operation(summary = "Audit activity trail for a work item")
  List<ActivityResponse> activity(@PathVariable UUID id, Authentication authentication) {
    access.require(authentication.getName(), "work-item.read", "work-item", id.toString());
    return activityQuery.list(id).stream().map(ActivityResponse::from).toList();
  }

  @GetMapping("/{id}/attachments")
  @Operation(summary = "List attachments linked to a work item")
  List<AttachmentLinkResponse> listAttachments(
      @PathVariable UUID id,
      Authentication authentication
  ) {
    access.require(authentication.getName(), "work-item.read", "work-item", id.toString());
    access.require(authentication.getName(), "attachment.read", "attachment", null);
    return workItemAttachments.list(id).stream().map(AttachmentLinkResponse::from).toList();
  }

  @PostMapping("/{id}/attachments")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Link an existing attachment to a work item")
  AttachmentLinkResponse linkAttachment(
      @PathVariable UUID id,
      @Valid @RequestBody LinkAttachmentRequest request,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.update", "work-item", id.toString());
    access.require(actor, "attachment.read", "attachment", request.attachmentId().toString());
    return AttachmentLinkResponse.from(
        workItemAttachments.link(id, request.attachmentId(), actor)
    );
  }

  @DeleteMapping("/{id}/attachments/{attachmentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Unlink an attachment from a work item")
  void unlinkAttachment(
      @PathVariable UUID id,
      @PathVariable UUID attachmentId,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.update", "work-item", id.toString());
    workItemAttachments.unlink(id, attachmentId, actor);
  }

  @GetMapping("/{id}/watchers")
  @Operation(summary = "List subjects watching a work item")
  List<String> listWatchers(@PathVariable UUID id, Authentication authentication) {
    access.require(authentication.getName(), "work-item.read", "work-item", id.toString());
    return watchers.list(id);
  }

  @PostMapping("/{id}/watchers/me")
  @Operation(summary = "Watch a work item as the authenticated actor")
  List<String> watch(@PathVariable UUID id, Authentication authentication) {
    String actor = authentication.getName();
    access.require(actor, "work-item.read", "work-item", id.toString());
    return watchers.watch(id, actor);
  }

  @DeleteMapping("/{id}/watchers/me")
  @Operation(summary = "Stop watching a work item")
  List<String> unwatch(@PathVariable UUID id, Authentication authentication) {
    String actor = authentication.getName();
    access.require(actor, "work-item.read", "work-item", id.toString());
    return watchers.unwatch(id, actor);
  }

  record CreateRequest(
      @NotNull Type type,
      @NotBlank @Size(max = 240) String title,
      @NotBlank @Size(max = 12000) String description,
      @NotBlank @Size(max = 100) String service,
      Impact impact,
      Urgency urgency,
      @Size(max = 128) String assigneeId,
      @Size(max = 128) String teamId
  ) {}

  record UpdateRequest(
      @Size(max = 240) String title,
      @Size(max = 12000) String description,
      @Size(max = 100) String service,
      Impact impact,
      Urgency urgency
  ) {}

  record AssignRequest(
      @NotBlank @Size(max = 128) String assigneeId,
      @Size(max = 128) String teamId
  ) {}

  record LinkAttachmentRequest(@NotNull UUID attachmentId) {}

  record TransitionRequest(
      @NotNull State targetState,
      @Size(max = 80) String resolutionCode,
      @Size(max = 12000) String resolutionNotes
  ) {}

  record CommentRequest(
      @NotBlank @Size(max = 12000) String body
  ) {}
}
