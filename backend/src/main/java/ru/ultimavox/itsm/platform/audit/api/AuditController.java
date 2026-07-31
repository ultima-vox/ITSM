package ru.ultimavox.itsm.platform.audit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.audit.AuditEventRecord;
import ru.ultimavox.itsm.platform.audit.AuditQuery;
import ru.ultimavox.itsm.platform.authorization.AccessControl;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Platform — Audit")
class AuditController {

  private final AuditQuery query;
  private final AccessControl access;

  AuditController(AuditQuery query, AccessControl access) {
    this.query = query;
    this.access = access;
  }

  @GetMapping
  @Operation(summary = "List recent platform audit events (newest first)")
  List<AuditEventView> list(
      Authentication authentication,
      @RequestParam(required = false) String action,
      @RequestParam(required = false, defaultValue = "100") int limit,
      @RequestParam(required = false, defaultValue = "0") int offset
  ) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "audit.read", "audit_event", null);
    return query.list(action, limit, offset).stream().map(AuditEventView::from).toList();
  }

  @GetMapping("/actions")
  @Operation(summary = "Distinct audit action keys for filters")
  List<String> actions(Authentication authentication) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "audit.read", "audit_event", null);
    return query.distinctActions(100);
  }

  record AuditEventView(
      UUID id,
      Instant occurredAt,
      String at,
      String actorId,
      ActorView actor,
      String action,
      String objectType,
      String objectId,
      String objectLabel,
      String detail,
      UUID correlationId,
      Map<String, Object> beforeState,
      Map<String, Object> afterState
  ) {
    static AuditEventView from(AuditEventRecord r) {
      String label = firstString(r.afterState(), "number", "title", "name");
      if (label == null) {
        label = firstString(r.beforeState(), "number", "title", "name");
      }
      if (label == null) {
        label = r.objectId();
      }
      String detail = firstString(r.afterState(), "detail", "message", "toState", "status");
      if (detail == null && r.afterState() != null && !r.afterState().isEmpty()) {
        detail = "fields=" + r.afterState().keySet();
      }
      return new AuditEventView(
          r.id(),
          r.occurredAt(),
          r.occurredAt() == null ? null : r.occurredAt().toString(),
          r.actorId(),
          ActorView.fromSubject(r.actorId()),
          r.action(),
          r.objectType(),
          r.objectId(),
          label,
          detail,
          r.correlationId(),
          r.beforeState(),
          r.afterState()
      );
    }
  }

  record ActorView(String id, String name, String initials) {
    static ActorView fromSubject(String subject) {
      String id = subject == null || subject.isBlank() ? "unknown" : subject;
      String name = id;
      String initials;
      if (id.length() >= 2) {
        initials = id.substring(0, 2).toUpperCase();
      } else {
        initials = id.toUpperCase();
      }
      // Prefer last segment of email-like or UUID-ish ids for display
      int at = id.indexOf('@');
      if (at > 0) {
        name = id.substring(0, at);
        initials = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : name.toUpperCase();
      }
      return new ActorView(id, name, initials);
    }
  }

  private static String firstString(Map<String, Object> map, String... keys) {
    if (map == null || map.isEmpty()) {
      return null;
    }
    for (String key : keys) {
      Object v = map.get(key);
      if (v != null) {
        String s = String.valueOf(v).trim();
        if (!s.isEmpty() && !"null".equals(s)) {
          return s;
        }
      }
    }
    return null;
  }
}
