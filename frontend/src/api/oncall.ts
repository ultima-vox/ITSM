import { apiRequest, delay, isMockMode } from './client';
import {
  addMockOverride,
  deleteMockOverride,
  deleteMockPolicy,
  deleteMockSchedule,
  listMockOverrides,
  listMockPolicies,
  listMockSchedules,
  mockEscalationChain,
  mockOnCallNow,
  saveMockPolicy,
  saveMockSchedule,
} from '@/mock/oncall';
import type {
  EscalationPolicy,
  EscalationPolicyInput,
  EscalationResponder,
  OnCallOverride,
  OnCallSchedule,
  OnCallScheduleInput,
} from '@/types';

export async function fetchOnCallSchedules(signal?: AbortSignal): Promise<OnCallSchedule[]> {
  if (isMockMode()) {
    await delay(110);
    return listMockSchedules();
  }
  return (await apiRequest<OnCallSchedule[]>('/oncall/schedules', { signal })) ?? [];
}

export interface OnCallNow {
  scheduleKey: string;
  at: string;
  subject: string | null;
}

export async function fetchOnCallNow(
  scheduleKey: string,
  at?: string,
  signal?: AbortSignal,
): Promise<OnCallNow> {
  const when = at ?? new Date().toISOString();
  if (isMockMode()) {
    await delay(60);
    return { scheduleKey, at: when, subject: mockOnCallNow(scheduleKey, when) };
  }
  const qs = new URLSearchParams({ at: when });
  return apiRequest<OnCallNow>(`/oncall/schedules/${encodeURIComponent(scheduleKey)}/current?${qs}`, {
    signal,
  });
}

export async function saveOnCallSchedule(
  input: OnCallScheduleInput,
  existing: boolean,
): Promise<OnCallSchedule> {
  if (isMockMode()) {
    await delay(140);
    return saveMockSchedule(input);
  }
  return existing
    ? apiRequest<OnCallSchedule>(`/oncall/schedules/${encodeURIComponent(input.scheduleKey)}`, {
        method: 'PUT',
        body: input,
      })
    : apiRequest<OnCallSchedule>('/oncall/schedules', { method: 'POST', body: input });
}

export async function deleteOnCallSchedule(scheduleKey: string): Promise<void> {
  if (isMockMode()) {
    await delay(120);
    deleteMockSchedule(scheduleKey);
    return;
  }
  await apiRequest<void>(`/oncall/schedules/${encodeURIComponent(scheduleKey)}`, {
    method: 'DELETE',
  });
}

export async function fetchOnCallOverrides(
  scheduleKey: string,
  signal?: AbortSignal,
): Promise<OnCallOverride[]> {
  if (isMockMode()) {
    await delay(90);
    return listMockOverrides(scheduleKey);
  }
  return (
    (await apiRequest<OnCallOverride[]>(
      `/oncall/schedules/${encodeURIComponent(scheduleKey)}/overrides`,
      { signal },
    )) ?? []
  );
}

export async function addOnCallOverride(
  scheduleKey: string,
  input: { subject: string; startsAt: string; endsAt: string; reason?: string },
): Promise<OnCallOverride> {
  if (isMockMode()) {
    await delay(130);
    return addMockOverride(scheduleKey, input);
  }
  return apiRequest<OnCallOverride>(
    `/oncall/schedules/${encodeURIComponent(scheduleKey)}/overrides`,
    { method: 'POST', body: input },
  );
}

export async function deleteOnCallOverride(
  scheduleKey: string,
  overrideId: string,
): Promise<void> {
  if (isMockMode()) {
    await delay(110);
    deleteMockOverride(scheduleKey, overrideId);
    return;
  }
  await apiRequest<void>(
    `/oncall/schedules/${encodeURIComponent(scheduleKey)}/overrides/${overrideId}`,
    { method: 'DELETE' },
  );
}

export async function fetchEscalationPolicies(signal?: AbortSignal): Promise<EscalationPolicy[]> {
  if (isMockMode()) {
    await delay(110);
    return listMockPolicies();
  }
  return (await apiRequest<EscalationPolicy[]>('/oncall/policies', { signal })) ?? [];
}

export async function saveEscalationPolicy(
  input: EscalationPolicyInput,
  existing: boolean,
): Promise<EscalationPolicy> {
  if (isMockMode()) {
    await delay(140);
    return saveMockPolicy(input);
  }
  return existing
    ? apiRequest<EscalationPolicy>(`/oncall/policies/${encodeURIComponent(input.policyKey)}`, {
        method: 'PUT',
        body: input,
      })
    : apiRequest<EscalationPolicy>('/oncall/policies', { method: 'POST', body: input });
}

export async function deleteEscalationPolicy(policyKey: string): Promise<void> {
  if (isMockMode()) {
    await delay(120);
    deleteMockPolicy(policyKey);
    return;
  }
  await apiRequest<void>(`/oncall/policies/${encodeURIComponent(policyKey)}`, { method: 'DELETE' });
}

export async function fetchEscalationChain(
  policyKey: string,
  at?: string,
  signal?: AbortSignal,
): Promise<EscalationResponder[]> {
  const when = at ?? new Date().toISOString();
  if (isMockMode()) {
    await delay(80);
    return mockEscalationChain(policyKey, when);
  }
  const qs = new URLSearchParams({ at: when });
  return (
    (await apiRequest<EscalationResponder[]>(
      `/oncall/policies/${encodeURIComponent(policyKey)}/chain?${qs}`,
      { signal },
    )) ?? []
  );
}

/** `168` → `7d`; `24` → `1d`; `8` → `8h`. */
export function formatRotation(hours: number): string {
  if (hours % 24 === 0) return `${hours / 24}d`;
  return `${hours}h`;
}
