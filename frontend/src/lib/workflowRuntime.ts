/**
 * Bind mock workflow definitions to runtime transitions for work-item,
 * problem, and change (objectKey). UI statuses map to workflow states.
 */
import type {
  Change,
  ChangeStatus,
  Problem,
  WorkItem,
  WorkItemStatus,
  WorkflowDefinition,
  WorkflowTransition,
} from '@/types';
import { isMockMode } from '@/api/client';

let _mockWorkflow: typeof import('@/mock/workflow') | null = null;

function getActiveDefinition(objectKey: string): WorkflowDefinition | null {
  if (!isMockMode()) return null;
  if (_mockWorkflow) return _mockWorkflow.getActiveWorkflowDefinition(objectKey);
  return null;
}

function hasAnyWorkflowFor(objectKey: string): boolean {
  if (!isMockMode()) return false;
  if (_mockWorkflow) return _mockWorkflow.listWorkflowDefinitions().some((d) => d.objectKey === objectKey);
  return false;
}

export type TransitionSource = 'workflow' | 'fallback';
/** @deprecated Use TransitionSource */
export type WorkItemTransitionSource = TransitionSource;

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
  /**
   * Permission keys from requiredPermissions the principal lacks.
   * Empty when no check was requested or principal holds all grants.
   */
  missingPermissions: string[];
  enabled: boolean;
  /** True when target workflow state has no UI status mapping */
  unsupportedTarget: boolean;
  source: TransitionSource;
  /** Optional i18n key when policy (not fields) blocks the transition */
  policyBlockKey?: string | null;
}

/**
 * Which of `required` the principal does not hold.
 * `admin.full` short-circuits (holds every permission).
 * When `principalPermissions` is null/undefined, no permission gate is applied.
 */
export function missingRequiredPermissions(
  required: string[],
  principalPermissions?: string[] | null,
): string[] {
  if (principalPermissions == null) return [];
  if (!required.length) return [];
  if (principalPermissions.includes('admin.full')) return [];
  const held = new Set(principalPermissions);
  return required.filter((p) => !held.has(p));
}

/** Action-level permission keys for sticky bar stubs (not workflow edges). */
export const WORK_ITEM_ACTION_PERMISSIONS = {
  assign: ['work-item.assign'],
  escalate: ['work-item.update'],
} as const;

export interface WorkItemWorkflowRuntime {
  definition: WorkflowDefinition | null;
  /** Current workflow state key (NEW / PENDING / …) */
  currentState: string;
  /** Whether transitions come from active definition */
  source: TransitionSource;
  transitions: WorkItemRuntimeTransition[];
}

// ── Problem ──────────────────────────────────────────────────────────

/** Hard-coded fallback when no active problem workflow is available. */
export const HARD_CODED_PROBLEM_TRANSITIONS: Record<
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

/** UI problem status → workflow state (same map as work-item). */
export function uiProblemStatusToWorkflowState(status: WorkItemStatus): string {
  return uiStatusToWorkflowState(status);
}

/** Workflow state → problem UI status. */
export function workflowStateToProblemStatus(
  state: string,
): WorkItemStatus | null {
  return workflowStateToUiStatus(state);
}

export interface ProblemFieldOverrides {
  rootCause?: string;
  workaround?: string;
}

export interface ProblemRuntimeTransition {
  key: string;
  from: string;
  to: string;
  toStatus: WorkItemStatus | null;
  requiredFields: string[];
  requiredPermissions: string[];
  missingFields: string[];
  missingPermissions: string[];
  enabled: boolean;
  unsupportedTarget: boolean;
  source: TransitionSource;
  policyBlockKey?: string | null;
}

export interface ProblemWorkflowRuntime {
  definition: WorkflowDefinition | null;
  currentState: string;
  source: TransitionSource;
  transitions: ProblemRuntimeTransition[];
}

// ── Change ───────────────────────────────────────────────────────────

/** Hard-coded fallback when no active change workflow is available. */
export const HARD_CODED_CHANGE_TRANSITIONS: Record<
  ChangeStatus,
  ChangeStatus[]
> = {
  draft: ['cab_review', 'scheduled', 'cancelled'],
  cab_review: ['scheduled', 'draft', 'cancelled'],
  scheduled: ['in_progress', 'cancelled'],
  in_progress: ['completed', 'cancelled'],
  completed: [],
  cancelled: [],
};

/** UI change status → workflow state key (mock definition). */
export function uiChangeStatusToWorkflowState(status: ChangeStatus): string {
  switch (status) {
    case 'draft':
      return 'DRAFT';
    case 'cab_review':
      return 'CAB_REVIEW';
    case 'scheduled':
      return 'SCHEDULED';
    case 'in_progress':
      return 'IN_PROGRESS';
    case 'completed':
      return 'COMPLETED';
    case 'cancelled':
      return 'CANCELLED';
    default:
      return 'DRAFT';
  }
}

/** Workflow state key → UI change status (null if not representable). */
export function workflowStateToChangeStatus(
  state: string,
): ChangeStatus | null {
  switch (state.toUpperCase()) {
    case 'DRAFT':
      return 'draft';
    case 'CAB_REVIEW':
      return 'cab_review';
    case 'SCHEDULED':
      return 'scheduled';
    case 'IN_PROGRESS':
    case 'IMPLEMENTING':
      return 'in_progress';
    case 'COMPLETED':
    case 'CLOSED':
      return 'completed';
    case 'CANCELLED':
    case 'CANCELED':
    case 'REJECTED':
      return 'cancelled';
    default:
      return null;
  }
}

export interface ChangeFieldOverrides {
  implementationPlan?: string;
  backoutPlan?: string;
}

export interface ChangeRuntimeTransition {
  key: string;
  from: string;
  to: string;
  toStatus: ChangeStatus | null;
  requiredFields: string[];
  requiredPermissions: string[];
  missingFields: string[];
  missingPermissions: string[];
  enabled: boolean;
  unsupportedTarget: boolean;
  source: TransitionSource;
  policyBlockKey?: string | null;
}

export interface ChangeWorkflowRuntime {
  definition: WorkflowDefinition | null;
  currentState: string;
  source: TransitionSource;
  transitions: ChangeRuntimeTransition[];
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
  principalPermissions?: string[] | null,
): WorkItemRuntimeTransition {
  const toStatus = workflowStateToUiStatus(tr.to);
  const missing = missingRequiredFields(item, tr.requiredFields);
  const missingPerms = missingRequiredPermissions(
    tr.requiredPermissions,
    principalPermissions,
  );
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
    missingPermissions: missingPerms,
    enabled:
      !unsupportedTarget &&
      blockingMissing.length === 0 &&
      missingPerms.length === 0,
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
    missingPermissions: [],
    enabled: true,
    unsupportedTarget: false,
    source: 'fallback',
  };
}

/**
 * Available next transitions for a work item from the active workflow
 * definition (objectKey `work-item`), or hard-coded fallback when inactive.
 *
 * Pass `permissions` (principal grant keys from RBAC role) to enforce
 * transition `requiredPermissions`. Omit / null to skip the permission gate.
 */
export function getWorkItemRuntimeTransitions(
  item: WorkItem,
  opts?: {
    definition?: WorkflowDefinition | null;
    permissions?: string[] | null;
  },
): WorkItemWorkflowRuntime {
  const definition =
    opts?.definition !== undefined
      ? opts.definition
      : getActiveDefinition('work-item');
  const principalPermissions = opts?.permissions;

  const currentState = uiStatusToWorkflowState(item.status);

  if (definition && definition.active) {
    const outgoing = definition.transitions.filter(
      (tr) => tr.from.toUpperCase() === currentState.toUpperCase(),
    );
    const transitions = outgoing.map((tr) =>
      fromWorkflowTransition(tr, item, principalPermissions),
    );
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

/** Outgoing transition to RESOLVED / resolved UI status, if any. */
export function findResolveTransition(
  runtime: WorkItemWorkflowRuntime,
): WorkItemRuntimeTransition | undefined {
  return runtime.transitions.find(
    (tr) =>
      tr.toStatus === 'resolved' || tr.to.toUpperCase() === 'RESOLVED',
  );
}

/** Prefer definition name/state list; used for chip labels. */
export function workflowStateLabelKey(
  state: string,
  ns: 'workItem' | 'problems' | 'changes' = 'workItem',
): string {
  return `${ns}.workflowState.${state.toUpperCase()}`;
}

/** Whether any work-item definition is currently active (for tests/UI). */
export function hasActiveWorkItemWorkflow(): boolean {
  return hasAnyWorkflowFor('work-item');
}

// ── Problem runtime ──────────────────────────────────────────────────

export function isProblemFieldPresent(
  problem: Problem,
  field: string,
  overrides?: ProblemFieldOverrides,
): boolean {
  const key = field.trim().toLowerCase();
  const rootCause = overrides?.rootCause ?? problem.rootCause;
  const workaround = overrides?.workaround ?? problem.workaround;
  switch (key) {
    case 'assignee_id':
    case 'assignee':
      return Boolean(problem.assignee?.id);
    case 'priority':
      return Boolean(problem.priority);
    case 'root_cause':
    case 'rootcause':
      return Boolean(rootCause?.trim());
    case 'workaround':
      return Boolean(workaround?.trim());
    case 'title':
      return Boolean(problem.title?.trim());
    case 'description':
      return Boolean(problem.description?.trim());
    case 'service':
      return Boolean(problem.service?.trim());
    case 'known_error':
    case 'knownerror':
      return problem.knownError === true;
    default:
      return false;
  }
}

export function missingProblemRequiredFields(
  problem: Problem,
  requiredFields: string[],
  overrides?: ProblemFieldOverrides,
): string[] {
  return requiredFields.filter(
    (f) => !isProblemFieldPresent(problem, f, overrides),
  );
}

function fromProblemWorkflowTransition(
  tr: WorkflowTransition,
  problem: Problem,
  overrides?: ProblemFieldOverrides,
  principalPermissions?: string[] | null,
): ProblemRuntimeTransition {
  const toStatus = workflowStateToProblemStatus(tr.to);
  const missing = missingProblemRequiredFields(
    problem,
    tr.requiredFields,
    overrides,
  );
  const missingPerms = missingRequiredPermissions(
    tr.requiredPermissions,
    principalPermissions,
  );
  const unsupportedTarget = toStatus == null;
  return {
    key: tr.key,
    from: tr.from,
    to: tr.to,
    toStatus,
    requiredFields: [...tr.requiredFields],
    requiredPermissions: [...tr.requiredPermissions],
    missingFields: missing,
    missingPermissions: missingPerms,
    enabled:
      !unsupportedTarget &&
      missing.length === 0 &&
      missingPerms.length === 0,
    unsupportedTarget,
    source: 'workflow',
  };
}

function fromProblemFallbackStatus(
  fromStatus: WorkItemStatus,
  toStatus: WorkItemStatus,
): ProblemRuntimeTransition {
  return {
    key: `to_${toStatus}`,
    from: uiProblemStatusToWorkflowState(fromStatus),
    to: uiProblemStatusToWorkflowState(toStatus),
    toStatus,
    requiredFields: [],
    requiredPermissions: [],
    missingFields: [],
    missingPermissions: [],
    enabled: true,
    unsupportedTarget: false,
    source: 'fallback',
  };
}

/**
 * Available next transitions for a problem from the active workflow
 * definition (objectKey `problem`), or hard-coded fallback when inactive.
 *
 * Pass field overrides (e.g. RCA drafts in the drawer) so required-field
 * checks reflect in-progress edits before save.
 */
export function getProblemRuntimeTransitions(
  problem: Problem,
  opts?: {
    definition?: WorkflowDefinition | null;
    fieldOverrides?: ProblemFieldOverrides;
    permissions?: string[] | null;
  },
): ProblemWorkflowRuntime {
  const definition =
    opts?.definition !== undefined
      ? opts.definition
      : getActiveDefinition('problem');
  const currentState = uiProblemStatusToWorkflowState(problem.status);

  if (definition && definition.active) {
    const outgoing = definition.transitions.filter(
      (tr) => tr.from.toUpperCase() === currentState.toUpperCase(),
    );
    const transitions = outgoing.map((tr) =>
      fromProblemWorkflowTransition(
        tr,
        problem,
        opts?.fieldOverrides,
        opts?.permissions,
      ),
    );
    return {
      definition,
      currentState,
      source: 'workflow',
      transitions,
    };
  }

  const next = HARD_CODED_PROBLEM_TRANSITIONS[problem.status] ?? [];
  return {
    definition: null,
    currentState,
    source: 'fallback',
    transitions: next.map((s) => fromProblemFallbackStatus(problem.status, s)),
  };
}

export function hasActiveProblemWorkflow(): boolean {
  return hasAnyWorkflowFor('problem');
}

// ── Change runtime ───────────────────────────────────────────────────

export function isChangeFieldPresent(
  change: Change,
  field: string,
  overrides?: ChangeFieldOverrides,
): boolean {
  const key = field.trim().toLowerCase();
  const plan = overrides?.implementationPlan ?? change.implementationPlan;
  const backout = overrides?.backoutPlan ?? change.backoutPlan;
  switch (key) {
    case 'assignee_id':
    case 'assignee':
      return Boolean(change.assignee?.id);
    case 'implementation_plan':
    case 'implementationplan':
      return Boolean(plan?.trim());
    case 'backout_plan':
    case 'backoutplan':
    case 'rollback_plan':
      return Boolean(backout?.trim());
    case 'window_start':
    case 'windowstart':
    case 'planned_start':
    case 'plannedstart':
      return Boolean(change.plannedStart?.trim());
    case 'window_end':
    case 'windowend':
    case 'planned_end':
    case 'plannedend':
      return Boolean(change.plannedEnd?.trim());
    case 'title':
      return Boolean(change.title?.trim());
    case 'description':
      return Boolean(change.description?.trim());
    case 'service':
      return Boolean(change.service?.trim());
    case 'risk':
      return Boolean(change.risk);
    default:
      return false;
  }
}

export function missingChangeRequiredFields(
  change: Change,
  requiredFields: string[],
  overrides?: ChangeFieldOverrides,
): string[] {
  return requiredFields.filter(
    (f) => !isChangeFieldPresent(change, f, overrides),
  );
}

/**
 * Product policy gates for change (aligned with mock store).
 * Returns an i18n error key when blocked, else null.
 */
export function changeRuntimePolicyBlock(
  change: Change,
  toStatus: ChangeStatus,
  overrides?: ChangeFieldOverrides,
): string | null {
  const plan = (
    overrides?.implementationPlan ?? change.implementationPlan
  )?.trim();
  const backout = (overrides?.backoutPlan ?? change.backoutPlan)?.trim();

  // Normal cannot draft → schedule (must pass CAB).
  if (
    toStatus === 'scheduled' &&
    change.type === 'normal' &&
    change.status === 'draft'
  ) {
    return 'changes.validation.cabRequired';
  }
  if (toStatus === 'scheduled') {
    if (!plan) return 'changes.validation.planRequired';
    if (!backout) return 'changes.validation.backoutRequired';
    if (change.cabRejected) return 'changes.validation.cabRejected';
    if (change.type === 'normal' && !change.cabApproved) {
      return 'changes.validation.cabApprovalRequired';
    }
  }
  if (toStatus === 'cab_review' && !plan) {
    return 'changes.validation.planRequired';
  }
  return null;
}

function fromChangeWorkflowTransition(
  tr: WorkflowTransition,
  change: Change,
  overrides?: ChangeFieldOverrides,
  principalPermissions?: string[] | null,
): ChangeRuntimeTransition {
  const toStatus = workflowStateToChangeStatus(tr.to);
  const missing = missingChangeRequiredFields(
    change,
    tr.requiredFields,
    overrides,
  );
  const missingPerms = missingRequiredPermissions(
    tr.requiredPermissions,
    principalPermissions,
  );
  const unsupportedTarget = toStatus == null;
  const policyBlockKey =
    toStatus != null
      ? changeRuntimePolicyBlock(change, toStatus, overrides)
      : null;
  return {
    key: tr.key,
    from: tr.from,
    to: tr.to,
    toStatus,
    requiredFields: [...tr.requiredFields],
    requiredPermissions: [...tr.requiredPermissions],
    missingFields: missing,
    missingPermissions: missingPerms,
    enabled:
      !unsupportedTarget &&
      missing.length === 0 &&
      missingPerms.length === 0 &&
      !policyBlockKey,
    unsupportedTarget,
    source: 'workflow',
    policyBlockKey,
  };
}

function fromChangeFallbackStatus(
  change: Change,
  toStatus: ChangeStatus,
  overrides?: ChangeFieldOverrides,
): ChangeRuntimeTransition {
  const policyBlockKey = changeRuntimePolicyBlock(change, toStatus, overrides);
  // Mirror page behaviour: hide schedule for normal draft via policy disable
  // (fallback still lists the edge so bulk/store stay aligned).
  return {
    key: `to_${toStatus}`,
    from: uiChangeStatusToWorkflowState(change.status),
    to: uiChangeStatusToWorkflowState(toStatus),
    toStatus,
    requiredFields: [],
    requiredPermissions: [],
    missingFields: [],
    missingPermissions: [],
    enabled: !policyBlockKey,
    unsupportedTarget: false,
    source: 'fallback',
    policyBlockKey,
  };
}

/**
 * Available next transitions for a change from the active workflow
 * definition (objectKey `change`), or hard-coded fallback when inactive.
 */
export function getChangeRuntimeTransitions(
  change: Change,
  opts?: {
    definition?: WorkflowDefinition | null;
    fieldOverrides?: ChangeFieldOverrides;
    permissions?: string[] | null;
  },
): ChangeWorkflowRuntime {
  const definition =
    opts?.definition !== undefined
      ? opts.definition
      : getActiveDefinition('change');
  const currentState = uiChangeStatusToWorkflowState(change.status);

  if (definition && definition.active) {
    const outgoing = definition.transitions.filter(
      (tr) => tr.from.toUpperCase() === currentState.toUpperCase(),
    );
    const transitions = outgoing.map((tr) =>
      fromChangeWorkflowTransition(
        tr,
        change,
        opts?.fieldOverrides,
        opts?.permissions,
      ),
    );
    return {
      definition,
      currentState,
      source: 'workflow',
      transitions,
    };
  }

  const next = HARD_CODED_CHANGE_TRANSITIONS[change.status] ?? [];
  return {
    definition: null,
    currentState,
    source: 'fallback',
    transitions: next.map((s) =>
      fromChangeFallbackStatus(change, s, opts?.fieldOverrides),
    ),
  };
}

export function hasActiveChangeWorkflow(): boolean {
  return hasAnyWorkflowFor('change');
}

// Lazy-init mock store in mock mode
if (isMockMode()) {
  import('@/mock/workflow').then((m) => { _mockWorkflow = m; });
}
