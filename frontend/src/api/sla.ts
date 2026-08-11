/**
 * SLA admin API — live policies from backend; mock store for local-only edits.
 */
import { apiRequest, delay, isMockMode } from './client';
import {
  getWorkingCalendar as mockGetCalendar,
  listSlaPolicies as mockListPolicies,
  listWorkingCalendars as mockListCalendars,
  setSlaPolicyEnabled as mockSetEnabled,
  subscribeSlaPolicies as mockSubscribe,
  updateSlaPolicyTargets as mockUpdateTargets,
  DEFAULT_WORKING_CALENDAR,
} from '@/mock/sla';
import type { SlaPolicy, SlaTarget, WorkingCalendarMock } from '@/types';

interface BackendSlaTarget {
  metric: string;
  condition: string;
  targetMinutes: number;
  warningBeforeMinutes: number;
}

interface BackendSlaPolicy {
  id: string;
  key: string;
  calendarKey: string;
  enabled: boolean;
  version: number;
  targets: BackendSlaTarget[];
  pauseStates: string[];
}

function mapPolicy(dto: BackendSlaPolicy): SlaPolicy {
  return {
    id: dto.id,
    key: dto.key,
    calendarKey: dto.calendarKey ?? 'default-business',
    enabled: dto.enabled,
    version: dto.version,
    pauseStates: dto.pauseStates ?? [],
    name: dto.key,
    targets: (dto.targets ?? []).map((t) => ({
      metric: t.metric,
      condition: t.condition,
      targetHours: (t.targetMinutes ?? 0) / 60,
      warningBeforeHours: (t.warningBeforeMinutes ?? 0) / 60,
    })),
  };
}

export async function fetchSlaPolicies(): Promise<SlaPolicy[]> {
  if (isMockMode()) {
    await delay(120);
    return mockListPolicies();
  }
  const list = await apiRequest<BackendSlaPolicy[]>('/sla/policies');
  return (list ?? []).map(mapPolicy);
}

export function listWorkingCalendars(): WorkingCalendarMock[] {
  if (isMockMode()) return mockListCalendars();
  return [DEFAULT_WORKING_CALENDAR];
}

export function getWorkingCalendar(key: string): WorkingCalendarMock | undefined {
  if (isMockMode()) return mockGetCalendar(key) ?? undefined;
  return DEFAULT_WORKING_CALENDAR;
}

export function slaPoliciesWritable(): boolean {
  return true;
}

export async function updateSlaPolicyTargets(
  id: string,
  expectedVersion: number,
  targets: SlaTarget[],
): Promise<SlaPolicy | null> {
  if (isMockMode()) {
    await delay(80);
    return mockUpdateTargets(id, targets);
  }
  const changed = await apiRequest<BackendSlaPolicy>(
    `/sla/policies/${encodeURIComponent(id)}`,
    {
      method: 'PATCH',
      body: {
        expectedVersion,
        targets: targets.map((target) => ({
          metric: target.metric,
          condition: target.condition,
          targetMinutes: Math.round(target.targetHours * 60),
          warningBeforeMinutes: Math.round(target.warningBeforeHours * 60),
        })),
      },
    },
  );
  return mapPolicy(changed);
}

export async function setSlaPolicyEnabled(
  id: string,
  expectedVersion: number,
  enabled: boolean,
): Promise<SlaPolicy | null> {
  if (isMockMode()) {
    await delay(60);
    return mockSetEnabled(id, enabled);
  }
  const changed = await apiRequest<BackendSlaPolicy>(
    `/sla/policies/${encodeURIComponent(id)}`,
    { method: 'PATCH', body: { expectedVersion, enabled } },
  );
  return mapPolicy(changed);
}

export function subscribeSlaPolicies(listener: () => void): () => void {
  if (isMockMode()) return mockSubscribe(listener);
  return () => undefined;
}

export { DEFAULT_WORKING_CALENDAR };
