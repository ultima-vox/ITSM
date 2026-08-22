/**
 * Mock worklog store, shaped like backend `WorkItemWorklogService.Entry`.
 * Dev only — the live API never reads this module.
 */
import { getApiActorId } from '@/api/client';
import type { Worklog, WorklogSummary } from '@/types';

const MAX_MINUTES_PER_ENTRY = 1440;

const byWorkItem = new Map<string, Worklog[]>();

let sequence = 0;

function entries(workItemId: string): Worklog[] {
  const found = byWorkItem.get(workItemId);
  if (found) return found;
  const created: Worklog[] = [];
  byWorkItem.set(workItemId, created);
  return created;
}

function validate(minutes: number, startedAt: string): void {
  if (!Number.isInteger(minutes) || minutes <= 0 || minutes > MAX_MINUTES_PER_ENTRY) {
    throw new Error(`minutes must be between 1 and ${MAX_MINUTES_PER_ENTRY}`);
  }
  const started = new Date(startedAt).getTime();
  if (Number.isNaN(started)) throw new Error('startedAt is required');
  if (started > Date.now() + 5 * 60 * 1000) throw new Error('startedAt cannot be in the future');
}

function summarize(items: Worklog[]): WorklogSummary {
  const sorted = [...items].sort(
    (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime(),
  );
  return {
    items: sorted,
    totalMinutes: sorted.reduce((sum, item) => sum + item.minutes, 0),
    billableMinutes: sorted
      .filter((item) => item.billable)
      .reduce((sum, item) => sum + item.minutes, 0),
  };
}

export function listMockWorklogs(workItemId: string): WorklogSummary {
  return summarize(entries(workItemId));
}

export function logMockWorklog(
  workItemId: string,
  payload: { minutes: number; startedAt: string; note?: string; billable?: boolean },
): Worklog {
  validate(payload.minutes, payload.startedAt);
  sequence += 1;
  const now = new Date().toISOString();
  const created: Worklog = {
    id: `worklog-${sequence}`,
    workItemId,
    authorSubject: getApiActorId(),
    minutes: payload.minutes,
    startedAt: payload.startedAt,
    note: payload.note?.trim() || null,
    billable: payload.billable ?? false,
    createdAt: now,
    updatedAt: now,
  };
  entries(workItemId).push(created);
  return { ...created };
}

export function updateMockWorklog(
  workItemId: string,
  worklogId: string,
  payload: { minutes?: number; startedAt?: string; note?: string; billable?: boolean },
): Worklog {
  const list = entries(workItemId);
  const found = list.find((entry) => entry.id === worklogId);
  if (!found) throw new Error(`Worklog not found: ${worklogId}`);
  if (found.authorSubject !== getApiActorId()) {
    throw new Error('Only the author can change this worklog');
  }
  const minutes = payload.minutes ?? found.minutes;
  const startedAt = payload.startedAt ?? found.startedAt;
  validate(minutes, startedAt);
  found.minutes = minutes;
  found.startedAt = startedAt;
  if (payload.note !== undefined) found.note = payload.note.trim() || null;
  if (payload.billable !== undefined) found.billable = payload.billable;
  found.updatedAt = new Date().toISOString();
  return { ...found };
}

export function deleteMockWorklog(workItemId: string, worklogId: string): void {
  const list = entries(workItemId);
  const index = list.findIndex((entry) => entry.id === worklogId);
  if (index < 0) throw new Error(`Worklog not found: ${worklogId}`);
  if (list[index]!.authorSubject !== getApiActorId()) {
    throw new Error('Only the author can change this worklog');
  }
  list.splice(index, 1);
}
