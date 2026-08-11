/**
 * Bind mock SLA admin policies to work-item SLA chrome (S21 remainder).
 * When policies disabled/missing → hard-coded fallback targets (prior seed).
 */
import type {
  Priority,
  SlaPolicy,
  SlaTarget,
  WorkItemStatus,
  WorkingCalendarMock,
} from '@/types';
import {
  getSlaPolicy,
  getWorkingCalendar,
  listSlaPolicies,
} from '@/mock/sla';

export type SlaMetric = 'response' | 'resolution';

export interface SlaRuntimeTarget {
  metric: SlaMetric;
  /** Hours from policy (or fallback) */
  targetHours: number;
  warningBeforeHours: number;
  condition: string;
  /** Human-facing minutes for response-style display */
  targetMinutes: number;
  source: 'policy' | 'fallback';
  policy: SlaPolicy | null;
}

export interface WorkItemSlaRuntime {
  response: SlaRuntimeTarget;
  resolution: SlaRuntimeTarget;
  /** Resolution policy primary for policy label (or response if only one) */
  primaryPolicy: SlaPolicy | null;
  calendar: WorkingCalendarMock | null;
  /** UI status maps to workflow-ish pause state keys */
  paused: boolean;
  pauseStates: string[];
  source: 'policy' | 'fallback' | 'mixed';
}

const PRIORITY_COND: Record<Priority, string> = {
  critical: 'priority=CRITICAL',
  high: 'priority=HIGH',
  medium: 'priority=MEDIUM',
  low: 'priority=LOW',
};

/** Hard-coded fallbacks matching prior WID helpers (minutes / hours). */
const FALLBACK_RESPONSE_MINS: Record<Priority, number> = {
  critical: 15,
  high: 30,
  medium: 60,
  low: 120,
};

const FALLBACK_RESOLUTION_HOURS: Record<Priority, number> = {
  critical: 4,
  high: 8,
  medium: 24,
  low: 40,
};

function uiStatusToPauseKey(status: WorkItemStatus): string {
  switch (status) {
    case 'waiting':
      return 'PENDING';
    case 'new':
      return 'NEW';
    case 'in_progress':
      return 'IN_PROGRESS';
    case 'resolved':
      return 'RESOLVED';
    case 'closed':
      return 'CLOSED';
    case 'cancelled':
      return 'CANCELLED';
    default:
      return String(status).toUpperCase();
  }
}

function matchTarget(
  targets: SlaTarget[],
  metric: SlaMetric,
  priority: Priority,
): SlaTarget | null {
  const want = PRIORITY_COND[priority] ?? `priority=${priority.toUpperCase()}`;
  const metricRows = targets.filter(
    (t) => t.metric.toLowerCase() === metric,
  );
  const exact = metricRows.find(
    (t) => t.condition.replace(/\s/g, '').toUpperCase() === want.toUpperCase(),
  );
  if (exact) return exact;
  // Loose: condition contains priority token
  const token = priority.toUpperCase();
  const loose = metricRows.find((t) =>
    t.condition.toUpperCase().includes(token),
  );
  return loose ?? metricRows[0] ?? null;
}

function fallbackTarget(
  metric: SlaMetric,
  priority: Priority,
): SlaRuntimeTarget {
  if (metric === 'response') {
    const mins = FALLBACK_RESPONSE_MINS[priority] ?? 60;
    return {
      metric,
      targetHours: mins / 60,
      warningBeforeHours: mins / 60 / 4,
      condition: PRIORITY_COND[priority],
      targetMinutes: mins,
      source: 'fallback',
      policy: null,
    };
  }
  const hours = FALLBACK_RESOLUTION_HOURS[priority] ?? 24;
  return {
    metric,
    targetHours: hours,
    warningBeforeHours: hours / 4,
    condition: PRIORITY_COND[priority],
    targetMinutes: Math.round(hours * 60),
    source: 'fallback',
    policy: null,
  };
}

function fromPolicy(
  metric: SlaMetric,
  priority: Priority,
  policy: SlaPolicy | null,
): SlaRuntimeTarget {
  if (!policy || !policy.enabled) {
    return fallbackTarget(metric, priority);
  }
  const row = matchTarget(policy.targets, metric, priority);
  if (!row) return fallbackTarget(metric, priority);
  const hours = Math.max(0.01, Number(row.targetHours) || 0.01);
  return {
    metric,
    targetHours: hours,
    warningBeforeHours: Math.max(0, Number(row.warningBeforeHours) || 0),
    condition: row.condition,
    targetMinutes: Math.round(hours * 60),
    source: 'policy',
    policy,
  };
}

/**
 * Resolve active work-item SLA policies for a priority + UI status.
 * Prefer keys `work-item.response` / `work-item.resolution`; fall back to any enabled policy with matching metric rows.
 */
export function getWorkItemSlaRuntime(
  priority: Priority,
  status: WorkItemStatus,
  policies?: SlaPolicy[],
): WorkItemSlaRuntime {
  const all = (policies ?? listSlaPolicies()).filter((p) => p.enabled);
  const responsePolicy =
    all.find((p) => p.key === 'work-item.response') ??
    all.find((p) =>
      p.targets.some((t) => t.metric.toLowerCase() === 'response'),
    ) ??
    null;
  const resolutionPolicy =
    all.find((p) => p.key === 'work-item.resolution') ??
    all.find((p) =>
      p.targets.some((t) => t.metric.toLowerCase() === 'resolution'),
    ) ??
    null;

  const response = fromPolicy('response', priority, responsePolicy);
  const resolution = fromPolicy('resolution', priority, resolutionPolicy);

  const primaryPolicy = resolution.policy ?? response.policy;
  const pauseStates = primaryPolicy?.pauseStates ?? ['PENDING'];
  const pauseKey = uiStatusToPauseKey(status);
  const paused = pauseStates.map((s) => s.toUpperCase()).includes(pauseKey);

  const calendarKey =
    primaryPolicy?.calendarKey ??
    responsePolicy?.calendarKey ??
    resolutionPolicy?.calendarKey ??
    'default-business';
  const calendar = getWorkingCalendar(calendarKey);

  let source: WorkItemSlaRuntime['source'] = 'fallback';
  if (response.source === 'policy' && resolution.source === 'policy') {
    source = 'policy';
  } else if (response.source === 'policy' || resolution.source === 'policy') {
    source = 'mixed';
  }

  return {
    response,
    resolution,
    primaryPolicy,
    calendar,
    paused,
    pauseStates,
    source,
  };
}

/** Convenience: resolution policy by id/key for admin deep-link labels. */
export function resolveSlaPolicyLabel(policy: SlaPolicy | null): string {
  if (!policy) return '';
  return policy.name?.trim() || policy.key;
}

export function formatSlaHours(hours: number): string {
  if (hours < 1) {
    const m = Math.round(hours * 60);
    return `${m}m`;
  }
  if (Number.isInteger(hours)) return `${hours}h`;
  return `${hours.toFixed(2).replace(/\.?0+$/, '')}h`;
}

/** Re-export get for tests. */
export { getSlaPolicy };
