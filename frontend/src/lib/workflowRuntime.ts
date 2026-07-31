/**
 * Bind mock workflow definitions to runtime work-item transitions.
 * UI statuses map to workflow states (PENDING ↔ waiting).
 */
import {
  getActiveWorkflowDefinition,
  listWorkflowDefinitions,
} from '@/mock/workflow';
import type {
  WorkItem,
  WorkItemStatus,
  WorkflowDefinition,
  WorkflowTransition,
} from '@/types';

/** Hard-coded fallback when no active work-item workflow is available. */
export const HARD_CODED_WORK_ITEM_TRANSITIONS: Record<
  WorkItemStatus,
  WorkItemStatus[]
> = {
  new: ['in_progress', 'cancelled'],
  in_progress: ['waiting', 'resolved', 'cancelled'],
  waiting: ['in_progress', 'resolved', 'cancelled'],
  resolved: ['closed', 'in_progress'],
  closed: [],
  cancelled: [],
};

/** UI status → workflow state key */
export function uiStatusToWorkflowState(status: WorkItemStatus): string {
  switch (status) {
    case 'new':
      return 'NEW';
    case 'in_progress':
      return 'IN_PROGRESS';
    case 'waiting':
      return 'PENDING';
    case 'resolved':
      return 'RESOLVED';
    case 'closed':
      return 'CLOSED';
    case 'cancelled':
      return 'CANCELLED';
    default:
      return 'NEW';
  }
}

/** Workflow state key → UI status (null if not representable in UI). */
export function workflowStateToUiStatus(state: string): WorkItemStatus | null {
  switch (state.toUpperCase()) {
    case 'NEW':
      return 'new';
    case 'IN_PROGRESS':
      return 'in_progress';
    case 'PENDING':
      return 'waiting';
    case 'RESOLVED':
      return 'resolved';
    case 'CLOSED':
      return 'closed';
    case 'CANCELLED':
    case 'CANCELED':
      return 'cancelled';
    default:
      return null;
  }
}

export type WorkItemTransitionSource = 'workflow' | 'fallback';

export interface WorkItemRuntimeTransition {
  /** Transition key from definition, or synthetic `to_<status>` for fallback */
  key: string;
  from: string;
  to: string;
  /** Mapped UI status when target is representable */
  toStatus: WorkItemStatus | null;
  requiredFields: string[];
  requiredPermissions: string[];
  /** Fields present on the work item that fail requiredFields checks */
  missingFields: string[];
  enabled: boolean;
  /** True when target workflow state has no UI status mapping */
  unsupportedTarget: boolean;
  source: WorkItemTransitionSource;
}

export interface WorkItemWorkflowRuntime {
  definition: WorkflowDefinition | null;
  /** Current workflow state key (NEW / PENDING / …) */
  currentState: string;
  /** Whether transitions come from active definition */
  source: WorkItemTransitionSource;
  transitions: WorkItemRuntimeTransition[];
}

/** Resolve a required-field key against a work item. */
export function isWorkItemFieldPresent(item: WorkItem, field: string): boolean {
  const key = field.trim().toLowerCase();
  switch (key) {
    case 'assignee_id':
    case 'assignee':
      return Boolean(item.assignee?.id);
    case 'priority':
      return Boolean(item.priority);
    case 'resolution_notes':
    case 'resolutionnotes':
      return Boolean(item.resolutionNotes?.trim());
    case 'title':
      return Boolean(item.title?.trim());
    case 'description':
      return Boolean(item.description?.trim());
    case 'service':
      return Boolean(item.service?.trim());
    case 'impact':
      return Boolean(item.impact);
    case 'urgency':
      return Boolean(item.urgency);
    default:
      // Unknown keys: treat as missing so illegal transitions stay blocked
      return false;
  }
}

export function missingRequiredFields(
  item: WorkItem,
  requiredFields: string[],
): string[] {
  return requiredFields.filter((f) => !isWorkItemFieldPresent(item, f));
}

function isResolutionNotesField(field: string): boolean {
  const key = field.trim().toLowerCase();
  return key === 'resolution_notes' || key === 'resolutionnotes';
}

function fromWorkflowTransition(
  tr: WorkflowTransition,
  item: WorkItem,
): WorkItemRuntimeTransition {
  const toStatus = workflowStateToUiStatus(tr.to);
  const missing = missingRequiredFields(item, tr.requiredFields);
  const unsupportedTarget = toStatus == null;
  // Resolve opens a modal that collects resolution notes — don't block the
  // button solely for that field; other required fields still disable it.
  const blockingMissing =
    toStatus === 'resolved'
      ? missing.filter((f) => !isResolutionNotesField(f))
      : missing;
  return {
    key: tr.key,
    from: tr.from,
    to: tr.to,
    toStatus,
    requiredFields: [...tr.requiredFields],
    requiredPermissions: [...tr.requiredPermissions],
    missingFields: missing,
    enabled: !unsupportedTarget && blockingMissing.length === 0,
    unsupportedTarget,
    source: 'workflow',
  };
}

function fromFallbackStatus(
  fromStatus: WorkItemStatus,
  toStatus: WorkItemStatus,
): WorkItemRuntimeTransition {
  return {
    key: `to_${toStatus}`,
    from: uiStatusToWorkflowState(fromStatus),
    to: uiStatusToWorkflowState(toStatus),
    toStatus,
    requiredFields: [],
    requiredPermissions: [],
    missingFields: [],
    enabled: true,
    unsupportedTarget: false,
    source: 'fallback',
  };
}

/**
 * Available next transitions for a work item from the active workflow
 * definition (objectKey `work-item`), or hard-coded fallback when inactive.
 */
export function getWorkItemRuntimeTransitions(
  item: WorkItem,
  opts?: { definition?: WorkflowDefinition | null },
): WorkItemWorkflowRuntime {
  const definition =
    opts?.definition !== undefined
      ? opts.definition
      : getActiveWorkflowDefinition('work-item');

  const currentState = uiStatusToWorkflowState(item.status);

  if (definition && definition.active) {
    const outgoing = definition.transitions.filter(
      (tr) => tr.from.toUpperCase() === currentState.toUpperCase(),
    );
    const transitions = outgoing.map((tr) => fromWorkflowTransition(tr, item));
    return {
      definition,
      currentState,
      source: 'workflow',
      transitions,
    };
  }

  const next = HARD_CODED_WORK_ITEM_TRANSITIONS[item.status] ?? [];
  return {
    definition: null,
    currentState,
    source: 'fallback',
    transitions: next.map((s) => fromFallbackStatus(item.status, s)),
  };
}

/** Prefer definition name/state list; used for chip labels. */
export function workflowStateLabelKey(state: string): string {
  return `workItem.workflowState.${state.toUpperCase()}`;
}

/** Whether any work-item definition is currently active (for tests/UI). */
export function hasActiveWorkItemWorkflow(): boolean {
  return listWorkflowDefinitions().some(
    (d) => d.objectKey === 'work-item' && d.active,
  );
}
