import { apiRequest, delay, isMockMode } from './client';
import {
  deleteMockWorklog,
  listMockWorklogs,
  logMockWorklog,
  updateMockWorklog,
} from '@/mock/worklogs';
import type { Worklog, WorklogSummary } from '@/types';

export interface LogTimePayload {
  minutes: number;
  startedAt: string;
  note?: string;
  billable?: boolean;
}

export interface UpdateWorklogPayload {
  minutes?: number;
  startedAt?: string;
  note?: string;
  billable?: boolean;
}

export async function fetchWorklogs(
  workItemId: string,
  signal?: AbortSignal,
): Promise<WorklogSummary> {
  if (isMockMode()) {
    await delay(90);
    return listMockWorklogs(workItemId);
  }
  return apiRequest<WorklogSummary>(`/work-items/${workItemId}/worklogs`, { signal });
}

export async function logTime(
  workItemId: string,
  payload: LogTimePayload,
): Promise<Worklog> {
  if (isMockMode()) {
    await delay(140);
    return logMockWorklog(workItemId, payload);
  }
  return apiRequest<Worklog>(`/work-items/${workItemId}/worklogs`, {
    method: 'POST',
    body: payload,
  });
}

export async function updateWorklog(
  workItemId: string,
  worklogId: string,
  payload: UpdateWorklogPayload,
): Promise<Worklog> {
  if (isMockMode()) {
    await delay(120);
    return updateMockWorklog(workItemId, worklogId, payload);
  }
  return apiRequest<Worklog>(`/work-items/${workItemId}/worklogs/${worklogId}`, {
    method: 'PATCH',
    body: payload,
  });
}

export async function deleteWorklog(workItemId: string, worklogId: string): Promise<void> {
  if (isMockMode()) {
    await delay(120);
    deleteMockWorklog(workItemId, worklogId);
    return;
  }
  await apiRequest<void>(`/work-items/${workItemId}/worklogs/${worklogId}`, {
    method: 'DELETE',
  });
}

/** `95` → `1h 35m`; `45` → `45m`. Keeps the grid readable without a date library. */
export function formatMinutes(minutes: number): string {
  if (!Number.isFinite(minutes) || minutes <= 0) return '0m';
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest}m`;
  if (rest === 0) return `${hours}h`;
  return `${hours}h ${rest}m`;
}
