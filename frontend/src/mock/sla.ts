/**
 * Mock SLA policies + working calendar + session-scoped target edits.
 * Shaped like backend `SlaPolicy` / `WorkingCalendar` (hours in UI).
 */
import type { SlaPolicy, SlaTarget, WorkingCalendarMock } from '@/types';

type Listener = () => void;

const listeners = new Set<Listener>();

/** Default business calendar — Mon–Fri 09:00–18:00 Europe/Moscow (backend seed). */
export const DEFAULT_WORKING_CALENDAR: WorkingCalendarMock = {
  key: 'default-business',
  zone: 'Europe/Moscow',
  workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
  startsAt: '09:00',
  endsAt: '18:00',
  holidays: [],
};

const CALENDARS: WorkingCalendarMock[] = [DEFAULT_WORKING_CALENDAR];

function target(
  metric: string,
  condition: string,
  targetHours: number,
  warningBeforeHours: number,
): SlaTarget {
  return { metric, condition, targetHours, warningBeforeHours };
}

/**
 * Seed policies: response + resolution by priority (from V10 SQL, expanded).
 * Minutes from backend seed converted to hours for admin edit.
 */
const SEED_POLICIES: SlaPolicy[] = [
  {
    id: 'sla-work-item-response',
    key: 'work-item.response',
    calendarKey: 'default-business',
    enabled: true,
    name: 'Work item — response',
    description:
      'First-response targets by priority under the default Moscow business calendar.',
    pauseStates: ['PENDING'],
    targets: [
      target('response', 'priority=CRITICAL', 0.25, 0.08),
      target('response', 'priority=HIGH', 1, 0.25),
      target('response', 'priority=MEDIUM', 4, 1),
      target('response', 'priority=LOW', 8, 2),
    ],
  },
  {
    id: 'sla-work-item-resolution',
    key: 'work-item.resolution',
    calendarKey: 'default-business',
    enabled: true,
    name: 'Work item — resolution',
    description:
      'Resolution targets by priority; clocks pause in PENDING.',
    pauseStates: ['PENDING'],
    targets: [
      target('resolution', 'priority=CRITICAL', 4, 1),
      target('resolution', 'priority=HIGH', 8, 2),
      target('resolution', 'priority=MEDIUM', 24, 4),
      target('resolution', 'priority=LOW', 40, 8),
    ],
  },
  {
    id: 'sla-change-implementation',
    key: 'change.implementation',
    calendarKey: 'default-business',
    enabled: true,
    name: 'Change — implementation window',
    description:
      'Soft targets for change completion relative to risk (mock policy).',
    pauseStates: ['CAB_REVIEW', 'SCHEDULED'],
    targets: [
      target('resolution', 'risk=HIGH', 8, 2),
      target('resolution', 'risk=MEDIUM', 24, 4),
      target('resolution', 'risk=LOW', 40, 8),
    ],
  },
];

let policies: SlaPolicy[] = SEED_POLICIES.map(clonePolicy);

function clonePolicy(p: SlaPolicy): SlaPolicy {
  return {
    ...p,
    pauseStates: [...p.pauseStates],
    targets: p.targets.map((t) => ({ ...t })),
  };
}

function notify() {
  listeners.forEach((fn) => fn());
}

export function subscribeSlaPolicies(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function listSlaPolicies(): SlaPolicy[] {
  return policies.map(clonePolicy);
}

export function getSlaPolicy(id: string): SlaPolicy | null {
  const found = policies.find((p) => p.id === id || p.key === id);
  return found ? clonePolicy(found) : null;
}

export function listWorkingCalendars(): WorkingCalendarMock[] {
  return CALENDARS.map((c) => ({
    ...c,
    workingDays: [...c.workingDays],
    holidays: [...c.holidays],
  }));
}

export function getWorkingCalendar(key: string): WorkingCalendarMock | null {
  const found = CALENDARS.find((c) => c.key === key);
  if (!found) return null;
  return {
    ...found,
    workingDays: [...found.workingDays],
    holidays: [...found.holidays],
  };
}

/**
 * Replace targets for a policy (session store). Hours must be positive numbers.
 */
export function updateSlaPolicyTargets(
  id: string,
  targets: SlaTarget[],
): SlaPolicy | null {
  const idx = policies.findIndex((p) => p.id === id || p.key === id);
  if (idx < 0) return null;

  const sanitized = targets.map((t) => ({
    metric: t.metric,
    condition: t.condition,
    targetHours: Math.max(0.01, Number(t.targetHours) || 0.01),
    warningBeforeHours: Math.max(0, Number(t.warningBeforeHours) || 0),
  }));

  policies[idx] = {
    ...policies[idx],
    targets: sanitized,
  };
  notify();
  return clonePolicy(policies[idx]);
}

export function resetSlaPolicies(): void {
  policies = SEED_POLICIES.map(clonePolicy);
  notify();
}
