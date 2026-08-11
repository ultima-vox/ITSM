package ru.ultimavox.itsm.servicedesk.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.ultimavox.itsm.servicedesk.application.WorkItemActivityQuery;
import ru.ultimavox.itsm.servicedesk.application.WorkItemAttachmentService;
import ru.ultimavox.itsm.servicedesk.application.WorkItemQuery;
import ru.ultimavox.itsm.servicedesk.application.WorkItemStatsQuery;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItemComment;

final class WorkItemResponses {

  private WorkItemResponses() {}

  record WorkItemResponse(
      UUID id,
      String number,
      String type,
      String title,
      String description,
      String service,
      String state,
      String priority,
      String impact,
      String urgency,
      String assigneeId,
      String requesterId,
      String teamId,
      String resolutionCode,
      String resolutionNotes,
      boolean escalated,
      Instant createdAt,
      Instant updatedAt,
      Instant closedAt
  ) {
    static WorkItemResponse from(WorkItem item) {
      return new WorkItemResponse(
          item.id(),
          item.number(),
          item.type().name(),
          item.title(),
          item.description(),
          item.service(),
          item.state().name(),
          item.priority().name(),
          item.impact().name(),
          item.urgency().name(),
          item.assigneeId(),
          item.requesterId(),
          item.teamId(),
          item.resolutionCode(),
          item.resolutionNotes(),
          item.escalated(),
          item.createdAt(),
          item.updatedAt(),
          item.closedAt()
      );
    }
  }

  record WorkItemPageResponse(
      List<WorkItemResponse> items,
      long total,
      int page,
      int size
  ) {
    static WorkItemPageResponse from(WorkItemQuery.PageResult page) {
      return new WorkItemPageResponse(
          page.items().stream().map(WorkItemResponse::from).toList(),
          page.total(),
          page.page(),
          page.size()
      );
    }
  }

  record CommentResponse(
      UUID id,
      UUID workItemId,
      String authorId,
      String body,
      boolean internal,
      Instant createdAt
  ) {
    static CommentResponse from(WorkItemComment comment) {
      return new CommentResponse(
          comment.id(),
          comment.workItemId(),
          comment.authorId(),
          comment.body(),
          comment.internal(),
          comment.createdAt()
      );
    }
  }

  record ActivityResponse(
      UUID id,
      Instant occurredAt,
      String actorId,
      String action,
      Map<String, Object> before,
      Map<String, Object> after,
      UUID correlationId
  ) {
    static ActivityResponse from(WorkItemActivityQuery.ActivityEntry entry) {
      return new ActivityResponse(
          entry.id(),
          entry.occurredAt(),
          entry.actorId(),
          entry.action(),
          entry.before(),
          entry.after(),
          entry.correlationId()
      );
    }
  }

  record AttachmentLinkResponse(
      UUID id,
      String filename,
      String contentType,
      long size,
      String objectKey,
      String linkedBy,
      Instant linkedAt,
      String scanStatus,
      String scanEngine
  ) {
    static AttachmentLinkResponse from(WorkItemAttachmentService.LinkedAttachment link) {
      var a = link.attachment();
      return new AttachmentLinkResponse(
          a.id(),
          a.filename(),
          a.contentType(),
          a.sizeBytes(),
          a.storageKey(),
          link.linkedBy(),
          link.linkedAt(),
          a.scanStatus() == null ? "PENDING" : a.scanStatus().name(),
          a.scanEngine()
      );
    }
  }

  record StatsResponse(long open, long dueToday, long breached, Double csat) {
    static StatsResponse from(WorkItemStatsQuery.Stats stats) {
      return new StatsResponse(stats.open(), stats.dueToday(), stats.breached(), stats.csat());
    }
  }
}
