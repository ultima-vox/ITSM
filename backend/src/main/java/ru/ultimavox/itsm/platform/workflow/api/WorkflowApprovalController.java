package ru.ultimavox.itsm.platform.workflow.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.workflow.WorkflowApprovalService;
import ru.ultimavox.itsm.platform.workflow.WorkflowApprovalService.ApprovalView;
import ru.ultimavox.itsm.platform.workflow.WorkflowApprovalService.Decision;
import ru.ultimavox.itsm.platform.workflow.WorkflowTransitionException;

@RestController
@RequestMapping("/api/v1/workflow")
@Tag(name = "Platform — Workflow Approvals")
class WorkflowApprovalController {
  private final WorkflowApprovalService approvals;
  private final AccessControl access;

  WorkflowApprovalController(WorkflowApprovalService approvals, AccessControl access) {
    this.approvals = approvals;
    this.access = access;
  }

  @GetMapping("/instances/{objectType}/{objectId}/approvals")
  @Operation(summary = "List parallel approval requests for a workflow instance")
  List<ApprovalView> list(Authentication authentication, @PathVariable String objectType,
                          @PathVariable String objectId) {
    access.require(authentication.getName(), "workflow.read", "workflow_instance", objectType + "/" + objectId);
    return approvals.list(objectType, objectId);
  }

  @PostMapping("/instances/{objectType}/{objectId}/approvals")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Request approval for a guarded transition")
  ApprovalView request(Authentication authentication, @PathVariable String objectType,
                       @PathVariable String objectId, @RequestBody RequestApproval body) {
    String actor = authentication.getName();
    access.require(actor, "workflow.approval.request", "workflow_instance", objectType + "/" + objectId);
    try {
      return approvals.request(objectType, objectId, body.transitionKey(), actor);
    } catch (WorkflowTransitionException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @PostMapping("/approvals/{id}/votes")
  @Operation(summary = "Cast an assigned immutable workflow approval vote")
  ApprovalView vote(Authentication authentication, @PathVariable UUID id, @RequestBody VoteRequest body) {
    String actor = authentication.getName();
    access.require(actor, "workflow.approve", "workflow_approval", id.toString());
    try {
      return approvals.vote(id, actor, body.decision(), body.comment());
    } catch (WorkflowTransitionException | IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  record RequestApproval(String transitionKey) {}
  record VoteRequest(Decision decision, String comment) {}
}
