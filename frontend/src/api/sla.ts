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
  version?: number;
  targets: BackendSlaTarget[];
  pauseStates: string[];
}

function mapPolicy(dto: BackendSlaPolicy): SlaPolicy {
  return {
    id: dto.id,
    key: dto.key,
    calendarKey: dto.calendarKey ?? 'default-business',
    enabled: dto.enabled,
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

/** Live mode is read-only for target edits until PATCH policy API lands. */
export function slaPoliciesWritable(): boolean {
  return isMockMode();
}

export async function updateSlaPolicyTargets(
  id: string,
  targets: SlaTarget[],
): Promise<SlaPolicy | null> {
  if (isMockMode()) {
    await delay(80);
    return mockUpdateTargets(id, targets);
  }
  throw new Error('module.errors.bulkLiveUnsupported');
}

export async function setSlaPolicyEnabled(
  id: string,
  enabled: boolean,
): Promise<SlaPolicy | null> {
  if (isMockMode()) {
    await delay(60);
    return mockSetEnabled(id, enabled);
  }
  throw new Error('module.errors.bulkLiveUnsupported');
}

export function subscribeSlaPolicies(listener: () => void): () => void {
  if (isMockMode()) return mockSubscribe(listener);
  return () => undefined;
}

export { DEFAULT_WORKING_CALENDAR };
