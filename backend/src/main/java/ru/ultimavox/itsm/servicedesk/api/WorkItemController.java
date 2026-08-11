package ru.ultimavox.itsm.servicedesk.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
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
import ru.ultimavox.itsm.servicedesk.application.BulkWorkItemService;
import ru.ultimavox.itsm.servicedesk.application.CreateWorkItem;
import ru.ultimavox.itsm.servicedesk.application.EscalateWorkItem;
import ru.ultimavox.itsm.servicedesk.application.GetWorkItem;
import ru.ultimavox.itsm.servicedesk.application.ListWorkItemComments;
import ru.ultimavox.itsm.servicedesk.application.MajorIncidentService;
import ru.ultimavox.itsm.servicedesk.application.SubmitWorkItemSurvey;
import ru.ultimavox.itsm.servicedesk.application.TransitionWorkItem;
import ru.ultimavox.itsm.servicedesk.application.UpdateWorkItem;
import ru.ultimavox.itsm.servicedesk.application.WorkItemActivityQuery;
import ru.ultimavox.itsm.servicedesk.application.WorkItemAttachmentService;
import ru.ultimavox.itsm.servicedesk.application.WorkItemQuery;
import ru.ultimavox.itsm.servicedesk.application.WorkItemStatsQuery;
import ru.ultimavox.itsm.servicedesk.application.WorkItemCiLinkService;
import ru.ultimavox.itsm.servicedesk.application.DuplicateWorkItemQuery;
import ru.ultimavox.itsm.servicedesk.application.WorkItemLinkService;
import ru.ultimavox.itsm.servicedesk.application.WorkItemWatcherService;
import ru.ultimavox.itsm.servicedesk.domain.WorkItemLink;
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
  private final EscalateWorkItem escalateWorkItem;
  private final TransitionWorkItem transitionWorkItem;
  private final AddWorkItemComment addWorkItemComment;
  private final ListWorkItemComments listWorkItemComments;
  private final WorkItemActivityQuery activityQuery;
  private final WorkItemStatsQuery statsQuery;
  private final WorkItemAttachmentService workItemAttachments;
  private final WorkItemWatcherService watchers;
  private final WorkItemLinkService links;
  private final WorkItemCiLinkService ciLinks;
  private final SubmitWorkItemSurvey surveys;
  private final DuplicateWorkItemQuery duplicates;
  private final MajorIncidentService majorIncidents;
  private final BulkWorkItemService bulkWorkItems;
  private final AccessControl access;

  WorkItemController(
      CreateWorkItem createWorkItem,
      WorkItemQuery workItemQuery,
      GetWorkItem getWorkItem,
      UpdateWorkItem updateWorkItem,
      AssignWorkItem assignWorkItem,
      EscalateWorkItem escalateWorkItem,
      TransitionWorkItem transitionWorkItem,
      AddWorkItemComment addWorkItemComment,
      ListWorkItemComments listWorkItemComments,
      WorkItemActivityQuery activityQuery,
      WorkItemStatsQuery statsQuery,
      WorkItemAttachmentService workItemAttachments,
      WorkItemWatcherService watchers,
      WorkItemLinkService links,
      WorkItemCiLinkService ciLinks,
      SubmitWorkItemSurvey surveys,
      DuplicateWorkItemQuery duplicates,
      MajorIncidentService majorIncidents,
      BulkWorkItemService bulkWorkItems,
      AccessControl access
  ) {
    this.createWorkItem = createWorkItem;
    this.workItemQuery = workItemQuery;
    this.getWorkItem = getWorkItem;
    this.updateWorkItem = updateWorkItem;
    this.assignWorkItem = assignWorkItem;
    this.escalateWorkItem = escalateWorkItem;
    this.transitionWorkItem = transitionWorkItem;
    this.addWorkItemComment = addWorkItemComment;
    this.listWorkItemComments = listWorkItemComments;
    this.activityQuery = activityQuery;
    this.statsQuery = statsQuery;
    this.workItemAttachments = workItemAttachments;
    this.watchers = watchers;
    this.links = links;
    this.ciLinks = ciLinks;
    this.surveys = surveys;
    this.duplicates = duplicates;
    this.majorIncidents = majorIncidents;
    this.bulkWorkItems = bulkWorkItems;
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
    String actor = authentication.getName();
    access.require(actor, "work-item.read", "work-item", null);
    boolean unrestricted = access.isAllowed(actor, "work-item.read.any", "work-item", null);
    return WorkItemPageResponse.from(
        workItemQuery.search(new WorkItemQuery.Filter(
            state, type, assigneeId, priority, q, unrestricted ? null : actor), page, size)
    );
  }

  @GetMapping("/stats")
  @Operation(summary = "Operator dashboard counters")
  StatsResponse stats(Authentication authentication) {
    access.require(authentication.getName(), "work-item.read.any", "work-item", null);
    return StatsResponse.from(statsQuery.stats());
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get work item by id")
  WorkItemResponse get(@PathVariable UUID id, Authentication authentication) {
    return WorkItemResponse.from(requireRead(authentication.getName(), id));
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

  @PostMapping("/{id}/escalate")
  @Operation(summary = "Escalate work item (HIGH/HIGH priority, start work if NEW)")
  WorkItemResponse escalate(@PathVariable UUID id, Authentication authentication) {
    String actor = authentication.getName();
    access.require(actor, "work-item.transition", "work-item", id.toString());
    try {
      return WorkItemResponse.from(escalateWorkItem.escalate(id, actor));
    } catch (IllegalStateException ex) {
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
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

  @GetMapping("/duplicates")
  @Operation(summary = "Find likely duplicate active work items")
  List<DuplicateWorkItemQuery.Match> duplicates(
      @RequestParam @NotBlank @Size(max=240) String title,
      @RequestParam(required=false,defaultValue="") @Size(max=2000) String description,
      @RequestParam(required=false) UUID excludeId,
      @RequestParam(defaultValue="5") int limit,
      Authentication authentication
  ) {
    access.require(authentication.getName(),"work-item.read.any","work-item",null);
    return duplicates.find(title,description,excludeId,limit);
  }

  @PostMapping("/{id}/survey")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Submit requester satisfaction survey")
  SubmitWorkItemSurvey.Result submitSurvey(
      @PathVariable UUID id, @Valid @RequestBody SurveyRequest request, Authentication authentication
  ) {
    String actor = authentication.getName();
    requireRead(actor, id);
    return surveys.submit(id, request.rating(), request.comment(), actor);
  }

  @PostMapping("/bulk/assign")
  @Operation(summary = "Atomically assign up to 200 work items")
  BulkWorkItemService.Result bulkAssign(
      @Valid @RequestBody BulkAssignRequest request, Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.assign", "work-item", null);
    return bulkWorkItems.assign(request.ids(), request.assigneeId(), request.teamId(), actor);
  }

  @PostMapping("/bulk/priority")
  @Operation(summary = "Atomically set priority on up to 200 work items")
  BulkWorkItemService.Result bulkPriority(
      @Valid @RequestBody BulkPriorityRequest request, Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.update", "work-item", null);
    return bulkWorkItems.setPriority(request.ids(), request.priority(), actor);
  }

  @GetMapping("/{id}/major-incident")
  @Operation(summary = "Get major incident declaration")
  ResponseEntity<MajorIncidentService.View> majorIncident(
      @PathVariable UUID id, Authentication authentication
  ) {
    requireRead(authentication.getName(), id);
    return ResponseEntity.of(majorIncidents.find(id));
  }

  @PostMapping("/{id}/major-incident")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Declare an incident as major")
  MajorIncidentService.View declareMajorIncident(
      @PathVariable UUID id,
      @Valid @RequestBody MajorIncidentRequest request,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.major", "work-item", id.toString());
    return majorIncidents.declare(id, request.commanderId(), request.summary(), actor);
  }

  @PostMapping("/{id}/major-incident/resolve")
  @Operation(summary = "Resolve a major incident declaration")
  MajorIncidentService.View resolveMajorIncident(
      @PathVariable UUID id, Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.major", "work-item", id.toString());
    return majorIncidents.resolve(id, actor);
  }

  @GetMapping("/{id}/comments")
  @Operation(summary = "List comments on a work item")
  List<CommentResponse> listComments(@PathVariable UUID id, Authentication authentication) {
    String actor = authentication.getName();
    requireRead(actor, id);
    boolean includeInternal = access.isAllowed(
        actor, "work-item.comment.internal", "work-item", id.toString());
    if (!includeInternal && !actor.equals(getWorkItem.get(id).requesterId())) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Only requester may read public replies");
    }
    return listWorkItemComments.list(id, includeInternal).stream().map(CommentResponse::from).toList();
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
    boolean canUseInternal = access.isAllowed(
        actor, "work-item.comment.internal", "work-item", id.toString());
    if (!canUseInternal && !actor.equals(getWorkItem.get(id).requesterId())) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Only requester may add a public reply");
    }
    if (request.internal()) {
      access.require(actor, "work-item.comment.internal", "work-item", id.toString());
    }
    return CommentResponse.from(
        addWorkItemComment.add(id, new AddWorkItemComment.Command(request.body(), request.internal()), actor)
    );
  }

  @GetMapping("/{id}/activity")
  @Operation(summary = "Audit activity trail for a work item")
  List<ActivityResponse> activity(@PathVariable UUID id, Authentication authentication) {
    String actor = authentication.getName();
    requireRead(actor, id);
    boolean includeInternal = access.isAllowed(
        actor, "work-item.comment.internal", "work-item", id.toString());
    if (!includeInternal && !actor.equals(getWorkItem.get(id).requesterId())) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Only requester may read public activity");
    }
    return activityQuery.list(id, includeInternal).stream().map(ActivityResponse::from).toList();
  }

  @GetMapping("/{id}/attachments")
  @Operation(summary = "List attachments linked to a work item")
  List<AttachmentLinkResponse> listAttachments(
      @PathVariable UUID id,
      Authentication authentication
  ) {
    requireRead(authentication.getName(), id);
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
    var item = requireRead(actor, id);
    if (!actor.equals(item.requesterId())) {
      access.require(actor, "work-item.update", "work-item", id.toString());
    }
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
    var item = requireRead(actor, id);
    if (!actor.equals(item.requesterId())) {
      access.require(actor, "work-item.update", "work-item", id.toString());
    }
    workItemAttachments.unlink(id, attachmentId, actor);
  }

  @GetMapping("/{id}/watchers")
  @Operation(summary = "List subjects watching a work item")
  List<String> listWatchers(@PathVariable UUID id, Authentication authentication) {
    requireRead(authentication.getName(), id);
    return watchers.list(id);
  }

  @PostMapping("/{id}/watchers/me")
  @Operation(summary = "Watch a work item as the authenticated actor")
  List<String> watch(@PathVariable UUID id, Authentication authentication) {
    String actor = authentication.getName();
    requireRead(actor, id);
    return watchers.watch(id, actor);
  }

  @DeleteMapping("/{id}/watchers/me")
  @Operation(summary = "Stop watching a work item")
  List<String> unwatch(@PathVariable UUID id, Authentication authentication) {
    String actor = authentication.getName();
    requireRead(actor, id);
    return watchers.unwatch(id, actor);
  }

  @GetMapping("/{id}/links")
  @Operation(summary = "List related work item links")
  List<LinkResponse> listLinks(@PathVariable UUID id, Authentication authentication) {
    requireRead(authentication.getName(), id);
    return links.listFor(id).stream().map(LinkResponse::from).toList();
  }

  @PostMapping("/{id}/links")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a related / duplicate / caused-by link")
  LinkResponse createLink(
      @PathVariable UUID id,
      @Valid @RequestBody CreateLinkRequest request,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.update", "work-item", id.toString());
    try {
      return LinkResponse.from(links.link(id, request.targetId(), request.linkType(), actor));
    } catch (IllegalArgumentException ex) {
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @DeleteMapping("/{id}/links/{linkId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove a work item link")
  void deleteLink(
      @PathVariable UUID id,
      @PathVariable UUID linkId,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.update", "work-item", id.toString());
    links.unlink(id, linkId, actor);
  }

  @GetMapping("/{id}/configuration-items")
  @Operation(summary = "List configuration items linked to a work item")
  List<UUID> listConfigurationItems(@PathVariable UUID id, Authentication authentication) {
    requireRead(authentication.getName(), id);
    return ciLinks.listCiIds(id);
  }

  @PostMapping("/{id}/configuration-items")
  @Operation(summary = "Link a configuration item to a work item")
  List<UUID> linkConfigurationItem(
      @PathVariable UUID id,
      @Valid @RequestBody LinkCiRequest request,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.update", "work-item", id.toString());
    try {
      return ciLinks.link(id, request.configurationItemId(), actor);
    } catch (IllegalArgumentException ex) {
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    }
  }

  @DeleteMapping("/{id}/configuration-items/{ciId}")
  @Operation(summary = "Unlink a configuration item from a work item")
  List<UUID> unlinkConfigurationItem(
      @PathVariable UUID id,
      @PathVariable UUID ciId,
      Authentication authentication
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.update", "work-item", id.toString());
    return ciLinks.unlink(id, ciId, actor);
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
      @NotBlank @Size(max = 12000) String body,
      boolean internal
  ) {}

  private ru.ultimavox.itsm.servicedesk.domain.WorkItem requireRead(String actor, UUID id) {
    var item = getWorkItem.get(id);
    access.requireOwned(actor, "work-item.read", "work-item", id.toString(), item.requesterId());
    return item;
  }

  record SurveyRequest(
      @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5) int rating,
      @Size(max = 2000) String comment
  ) {}

  record MajorIncidentRequest(
      @NotBlank @Size(max = 128) String commanderId,
      @NotBlank @Size(max = 2000) String summary
  ) {}

  record BulkAssignRequest(
      @NotEmpty @Size(max = 200) List<@NotNull UUID> ids,
      @NotBlank @Size(max = 128) String assigneeId,
      @Size(max = 128) String teamId
  ) {}

  record BulkPriorityRequest(
      @NotEmpty @Size(max = 200) List<@NotNull UUID> ids,
      @NotNull Priority priority
  ) {}

  record CreateLinkRequest(
      @NotNull UUID targetId,
      @NotNull WorkItemLink.Type linkType
  ) {}

  record LinkCiRequest(@NotNull UUID configurationItemId) {}

  record LinkResponse(
      UUID id,
      UUID sourceId,
      UUID targetId,
      String linkType,
      String createdBy,
      Instant createdAt
  ) {
    static LinkResponse from(WorkItemLink link) {
      return new LinkResponse(
          link.id(),
          link.sourceId(),
          link.targetId(),
          link.linkType().name(),
          link.createdBy(),
          link.createdAt()
      );
    }
  }
}
