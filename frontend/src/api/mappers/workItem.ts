/**
 * Map backend Service Desk WorkItemResponse (UPPERCASE enums, camelCase JSON)
 * to frontend WorkItem types (lowercase).
 */

import type {
  DashboardMetrics,
  ImpactLevel,
  Person,
  Priority,
  SlaState,
  UrgencyLevel,
  WorkItem,
  WorkItemActivity,
  WorkItemComment,
  WorkItemType,
  WorkItemStatus,
} from '@/types';
import { resolveUserSync } from '@/api/users';

/** Backend WorkItemResponse (JSON camelCase, enums as names). */
export interface BackendWorkItem {
  id: string;
  number: string;
  type: string;
  title: string;
  description: string;
  service: string;
  state: string;
  priority: string;
  impact?: string | null;
  urgency?: string | null;
  assigneeId?: string | null;
  requesterId?: string | null;
  teamId?: string | null;
  resolutionCode?: string | null;
  resolutionNotes?: string | null;
  escalated?: boolean | null;
  slaState?: SlaState | null;
  slaDueAt?: string | null;
  slaWarningAt?: string | null;
  createdAt: string;
  updatedAt: string;
  closedAt?: string | null;
}

export interface BackendWorkItemPage {
  items: BackendWorkItem[];
  total: number;
  page: number;
  size: number;
}

export interface BackendComment {
  id: string;
  workItemId: string;
  authorId: string;
  body: string;
  internal: boolean;
  createdAt: string;
}

export interface BackendActivity {
  id: string;
  occurredAt: string;
  actorId: string;
  action: string;
  before?: Record<string, unknown> | null;
  after?: Record<string, unknown> | null;
  correlationId?: string | null;
}

export interface BackendStats {
  open: number;
  dueToday: number;
  breached: number;
  csat?: number | null;
}

export interface BackendCreated {
  id: string;
  number: string;
  state: string;
  priority?: string;
}

function initialsFromId(id: string): string {
  const cleaned = id.replace(/[^a-zA-Z0-9]/g, '');
  if (cleaned.length >= 2) return cleaned.slice(0, 2).toUpperCase();
  if (cleaned.length === 1) return (cleaned + cleaned).toUpperCase();
  return '??';
}

/** Resolve person from ID. Uses pre-warmed cache in live mode. */
export function personFromId(id: string | null | undefined): Person | null {
  if (id == null || id === '') return null;
  const cached = resolveUserSync(id);
  if (cached.name !== id) return cached;
  return {
    id,
    name: id,
    initials: initialsFromId(id),
  };
}

export function mapBackendType(type: string): WorkItemType {
  const t = type.toUpperCase();
  if (t === 'SERVICE_REQUEST' || t === 'REQUEST') return 'request';
  if (t === 'CHANGE') return 'change';
  if (t === 'PROBLEM') return 'problem';
  return 'incident';
}

export function mapFrontendType(kind: 'incident' | 'request'): string {
  return kind === 'incident' ? 'INCIDENT' : 'SERVICE_REQUEST';
}

export function mapBackendState(state: string): WorkItemStatus {
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
      return 'new';
  }
}

export function mapFrontendState(status: WorkItemStatus): string {
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

export function mapBackendPriority(priority: string): Priority {
  switch (priority.toUpperCase()) {
    case 'CRITICAL':
      return 'critical';
    case 'HIGH':
      return 'high';
    case 'LOW':
      return 'low';
    default:
      return 'medium';
  }
}

export function mapFrontendPriority(priority: Priority): string {
  return priority.toUpperCase();
}

export function mapBackendImpact(impact?: string | null): ImpactLevel {
  switch ((impact ?? 'MEDIUM').toUpperCase()) {
    case 'HIGH':
      return 'high';
    case 'LOW':
      return 'low';
    default:
      return 'medium';
  }
}

export function mapBackendUrgency(urgency?: string | null): UrgencyLevel {
  switch ((urgency ?? 'MEDIUM').toUpperCase()) {
    case 'HIGH':
      return 'high';
    case 'LOW':
      return 'low';
    default:
      return 'medium';
  }
}

export function mapFrontendLevel(level: ImpactLevel | UrgencyLevel): string {
  return level.toUpperCase();
}

/**
 * No SLA fields on WorkItemResponse — derive a simple heuristic.
 * Closed/resolved → met; critical open → at_risk; else on_track.
 */
export function deriveSlaState(
  state: WorkItemStatus,
  priority: Priority,
): SlaState {
  if (state === 'resolved' || state === 'closed' || state === 'cancelled') {
    return 'met';
  }
  if (priority === 'critical') return 'at_risk';
  return 'on_track';
}

function slaTargetFor(priority: Priority, state: WorkItemStatus): string {
  if (state === 'resolved' || state === 'closed' || state === 'cancelled') {
    return '—';
  }
  switch (priority) {
    case 'critical':
      return '01:00';
    case 'high':
      return '04:00';
    case 'low':
      return '24:00';
    default:
      return '08:00';
  }
}

export function mapWorkItem(dto: BackendWorkItem): WorkItem {
  const status = mapBackendState(dto.state);
  const priority = mapBackendPriority(dto.priority);
  const requester =
    personFromId(dto.requesterId) ??
    ({
      id: 'unknown',
      name: 'unknown',
      initials: '??',
    } satisfies Person);

  return {
    id: String(dto.id),
    number: dto.number,
    title: dto.title,
    description: dto.description ?? '',
    type: mapBackendType(dto.type),
    priority,
    status,
    assignee: personFromId(dto.assigneeId),
    requester,
    service: dto.service,
    slaTarget: slaTargetFor(priority, status),
    slaState: dto.slaState ?? deriveSlaState(status, priority),
    slaDueAt: dto.slaDueAt ?? undefined,
    slaWarningAt: dto.slaWarningAt ?? undefined,
    updatedAt: dto.updatedAt,
    createdAt: dto.createdAt,
    teamId: dto.teamId ?? undefined,
    impact: mapBackendImpact(dto.impact),
    urgency: mapBackendUrgency(dto.urgency),
    resolutionNotes: dto.resolutionNotes ?? undefined,
    escalated: Boolean(dto.escalated),
    watchers: [],
    childTasks: [],
  };
}

export function mapComment(dto: BackendComment): WorkItemComment {
  return {
    id: String(dto.id),
    at: dto.createdAt,
    author:
      personFromId(dto.authorId) ?? {
        id: 'unknown',
        name: 'unknown',
        initials: '??',
      },
    body: dto.body,
    internal: dto.internal,
  };
}

export function mapActivity(dto: BackendActivity): WorkItemActivity {
  const action = dto.action ?? 'system';
  let kind: WorkItemActivity['kind'] = 'system';
  const lower = action.toLowerCase();
  if (lower.includes('assign')) kind = 'assignment';
  else if (lower.includes('comment')) kind = 'comment';
  else if (lower.includes('transition') || lower.includes('state')) kind = 'status';
  else if (lower.includes('sla')) kind = 'sla';
  else if (lower.includes('update') || lower.includes('field')) kind = 'field';

  return {
    id: String(dto.id),
    at: dto.occurredAt,
    actor:
      personFromId(dto.actorId) ?? {
        id: 'system',
        name: 'system',
        initials: 'SY',
      },
    kind,
    text: action,
    before: dto.before ?? undefined,
    after: dto.after ?? undefined,
  };
}

export function mapStats(dto: BackendStats): DashboardMetrics {
  return {
    open: Number(dto.open) || 0,
    openDelta: 0,
    dueToday: Number(dto.dueToday) || 0,
    dueUrgent: Number(dto.breached) || 0,
    breached: Number(dto.breached) || 0,
    satisfaction: dto.csat != null ? Number(dto.csat) : 0,
    flow: {
      new: 0,
      inProgress: 0,
      waiting: 0,
    },
  };
}
