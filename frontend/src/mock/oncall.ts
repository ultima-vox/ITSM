/**
 * Mock on-call store, shaped like backend `OnCallAdminService` records.
 * Dev only — the live API never reads this module.
 */
import type {
  EscalationPolicy,
  EscalationResponder,
  OnCallOverride,
  OnCallSchedule,
  OnCallScheduleInput,
  EscalationPolicyInput,
} from '@/types';

const ROTATION_START = '2026-08-03T09:00:00.000Z';

const schedules: OnCallSchedule[] = [
  {
    id: 'sched-platform',
    scheduleKey: 'platform',
    name: 'Platform rota',
    timeZone: 'Europe/Berlin',
    rotationHours: 168,
    rotationStart: ROTATION_START,
    active: true,
    participants: ['anna', 'boris', 'clara'],
  },
  {
    id: 'sched-night',
    scheduleKey: 'night',
    name: 'Night shift',
    timeZone: 'UTC',
    rotationHours: 24,
    rotationStart: ROTATION_START,
    active: true,
    participants: ['dmitri', 'elena'],
  },
];

const overrides: Record<string, OnCallOverride[]> = {
  platform: [],
  night: [],
};

const policies: EscalationPolicy[] = [
  {
    id: 'policy-work-item',
    policyKey: 'work-item.escalation',
    name: 'Work item escalation',
    active: true,
    steps: [
      { stepOrder: 0, delayMinutes: 0, targetType: 'SCHEDULE', targetRef: 'platform' },
      { stepOrder: 1, delayMinutes: 15, targetType: 'SUBJECT', targetRef: 'duty-manager' },
    ],
  },
];

let sequence = 0;

function findSchedule(scheduleKey: string): OnCallSchedule {
  const found = schedules.find((schedule) => schedule.scheduleKey === scheduleKey);
  if (!found) throw new Error(`Schedule not found: ${scheduleKey}`);
  return found;
}

function findPolicy(policyKey: string): EscalationPolicy {
  const found = policies.find((policy) => policy.policyKey === policyKey);
  if (!found) throw new Error(`Escalation policy not found: ${policyKey}`);
  return found;
}

function validateSchedule(input: OnCallScheduleInput): void {
  if (!input.scheduleKey.trim()) throw new Error('scheduleKey is required');
  if (!input.name.trim()) throw new Error('name is required');
  if (input.rotationHours < 1 || input.rotationHours > 8760) {
    throw new Error('rotationHours must be between 1 and 8760');
  }
  if (input.participants.length === 0) {
    throw new Error('a schedule needs at least one participant');
  }
}

function validatePolicy(input: EscalationPolicyInput): void {
  if (!input.policyKey.trim()) throw new Error('policyKey is required');
  if (!input.name.trim()) throw new Error('name is required');
  if (input.steps.length === 0) throw new Error('an escalation policy needs at least one step');
  let previous = -1;
  for (const step of input.steps) {
    if (step.delayMinutes < previous) {
      throw new Error('steps must be ordered by a non-decreasing delay');
    }
    previous = step.delayMinutes;
  }
}

/** Mirrors the backend rotation maths: advance one participant every rotationHours. */
export function rotationSubject(schedule: OnCallSchedule, at: string): string | null {
  if (!schedule.active || schedule.participants.length === 0) return null;
  const start = new Date(schedule.rotationStart).getTime();
  const when = new Date(at).getTime();
  if (when <= start) return schedule.participants[0]!;
  const elapsedHours = Math.floor((when - start) / 3_600_000);
  const periods = Math.floor(elapsedHours / schedule.rotationHours);
  const index = ((periods % schedule.participants.length) + schedule.participants.length)
    % schedule.participants.length;
  return schedule.participants[index]!;
}

export function listMockSchedules(): OnCallSchedule[] {
  return schedules.map((schedule) => ({ ...schedule, participants: [...schedule.participants] }));
}

export function mockOnCallNow(scheduleKey: string, at: string): string | null {
  const schedule = schedules.find((entry) => entry.scheduleKey === scheduleKey);
  if (!schedule) return null;
  const when = new Date(at).getTime();
  const cover = (overrides[scheduleKey] ?? []).find(
    (entry) => new Date(entry.startsAt).getTime() <= when && new Date(entry.endsAt).getTime() > when,
  );
  if (cover) return cover.subject;
  return rotationSubject(schedule, at);
}

export function saveMockSchedule(input: OnCallScheduleInput): OnCallSchedule {
  validateSchedule(input);
  const existing = schedules.find((schedule) => schedule.scheduleKey === input.scheduleKey);
  if (existing) {
    Object.assign(existing, input, { participants: [...input.participants] });
    return { ...existing };
  }
  sequence += 1;
  const created: OnCallSchedule = {
    id: `sched-${sequence}`,
    ...input,
    participants: [...input.participants],
  };
  schedules.push(created);
  overrides[created.scheduleKey] = [];
  return { ...created };
}

export function deleteMockSchedule(scheduleKey: string): void {
  const index = schedules.findIndex((schedule) => schedule.scheduleKey === scheduleKey);
  if (index < 0) throw new Error(`Schedule not found: ${scheduleKey}`);
  schedules.splice(index, 1);
  delete overrides[scheduleKey];
}

export function listMockOverrides(scheduleKey: string): OnCallOverride[] {
  findSchedule(scheduleKey);
  return [...(overrides[scheduleKey] ?? [])].sort(
    (a, b) => new Date(b.startsAt).getTime() - new Date(a.startsAt).getTime(),
  );
}

export function addMockOverride(
  scheduleKey: string,
  input: { subject: string; startsAt: string; endsAt: string; reason?: string },
): OnCallOverride {
  findSchedule(scheduleKey);
  if (!input.subject.trim()) throw new Error('subject is required');
  if (new Date(input.endsAt).getTime() <= new Date(input.startsAt).getTime()) {
    throw new Error('endsAt must be after startsAt');
  }
  sequence += 1;
  const created: OnCallOverride = {
    id: `override-${sequence}`,
    subject: input.subject.trim(),
    startsAt: input.startsAt,
    endsAt: input.endsAt,
    reason: input.reason?.trim() || null,
  };
  overrides[scheduleKey] = [...(overrides[scheduleKey] ?? []), created];
  return { ...created };
}

export function deleteMockOverride(scheduleKey: string, overrideId: string): void {
  const list = overrides[scheduleKey] ?? [];
  const index = list.findIndex((entry) => entry.id === overrideId);
  if (index < 0) throw new Error(`Override not found: ${overrideId}`);
  list.splice(index, 1);
}

export function listMockPolicies(): EscalationPolicy[] {
  return policies.map((policy) => ({ ...policy, steps: policy.steps.map((step) => ({ ...step })) }));
}

export function saveMockPolicy(input: EscalationPolicyInput): EscalationPolicy {
  validatePolicy(input);
  const steps = input.steps.map((step, index) => ({ ...step, stepOrder: index }));
  const existing = policies.find((policy) => policy.policyKey === input.policyKey);
  if (existing) {
    existing.name = input.name;
    existing.active = input.active;
    existing.steps = steps;
    return { ...existing, steps: steps.map((step) => ({ ...step })) };
  }
  sequence += 1;
  const created: EscalationPolicy = {
    id: `policy-${sequence}`,
    policyKey: input.policyKey,
    name: input.name,
    active: input.active,
    steps,
  };
  policies.push(created);
  return { ...created, steps: steps.map((step) => ({ ...step })) };
}

export function deleteMockPolicy(policyKey: string): void {
  const index = policies.findIndex((policy) => policy.policyKey === policyKey);
  if (index < 0) throw new Error(`Escalation policy not found: ${policyKey}`);
  policies.splice(index, 1);
}

export function mockEscalationChain(policyKey: string, at: string): EscalationResponder[] {
  const policy = findPolicy(policyKey);
  if (!policy.active) return [];
  const chain: EscalationResponder[] = [];
  for (const step of policy.steps) {
    if (step.targetType === 'SUBJECT') {
      chain.push({
        stepOrder: step.stepOrder,
        delayMinutes: step.delayMinutes,
        subject: step.targetRef,
        source: 'SUBJECT',
      });
      continue;
    }
    const subject = mockOnCallNow(step.targetRef, at);
    if (subject) {
      chain.push({
        stepOrder: step.stepOrder,
        delayMinutes: step.delayMinutes,
        subject,
        source: step.targetRef,
      });
    }
  }
  return chain;
}
