import { delay, isMockMode, apiRequest } from './client';
import {
  mapCabVote,
  mapChange,
  type BackendCabVote,
  type BackendChange,
} from './mappers/changes';
import {
  addChange as storeAddChange,
  bulkAssignChanges as storeBulkAssignChanges,
  bulkSetChangeStatus as storeBulkSetChangeStatus,
  type BulkStatusResult,
  castCabMemberVote as storeCastCabVote,
  listChanges,
  setChangeCabDecision as storeSetCabDecision,
  transitionChange as storeTransitionChange,
  updateChangeFields as storeUpdateChange,
} from '@/mock/store';
import type {
  CabVoteDecision,
  Change,
  ChangeStatus,
  CreateChangePayload,
} from '@/types';

/** Map UI change status to backend Change.Status enum. */
function toBackendChangeTarget(status: ChangeStatus): string {
  switch (status) {
    case 'draft':
      return 'DRAFT';
    case 'submitted':
      return 'SUBMITTED';
    case 'cab_review':
      return 'CAB_REVIEW';
    case 'approved':
      return 'APPROVED';
    case 'scheduled':
      return 'SCHEDULED';
    case 'implementing':
    case 'in_progress':
      return 'IMPLEMENTING';
    case 'review':
      return 'REVIEW';
    case 'completed':
      return 'CLOSED';
    case 'cancelled':
      return 'REJECTED';
    default:
      return 'DRAFT';
  }
}

function toBackendChangeType(type: string): string {
  return (type || 'normal').toUpperCase();
}

function toBackendRisk(risk: string): string {
  return (risk || 'medium').toUpperCase();
}

function toIsoOrUndefined(value?: string): string | undefined {
  if (!value?.trim()) return undefined;
  const v = value.trim();
  if (v.length === 16) return `${v}:00.000Z`;
  return v;
}

export async function fetchChanges(): Promise<Change[]> {
  if (isMockMode()) {
    await delay(220);
    return listChanges();
  }
  const resp = await apiRequest<{ items: BackendChange[]; total: number } | BackendChange[]>('/changes');
  const items = Array.isArray(resp) ? resp : (resp.items ?? []);
  const mapped = items.map(mapChange);
  return Promise.all(mapped.map(hydrateCabVotes));
}

async function hydrateCabVotes(change: Change): Promise<Change> {
  if (change.status !== 'cab_review') return change;
  try {
    const resp = await apiRequest<{
      votes: BackendCabVote[];
      approveCount: number;
      quorum: number;
    }>(`/changes/${encodeURIComponent(change.id)}/votes`);
    const cabVotes = (resp.votes ?? []).map(mapCabVote);
    const approves = cabVotes.filter((v) => v.decision === 'approve').length;
    return {
      ...change,
      cabVotes,
      cabApproved: approves >= CAB_QUORUM_APPROVES,
    };
  } catch {
    return change;
  }
}

/** Live schedule overlap from backend; mock returns []. */
export async function fetchScheduleConflicts(params: {
  start: string;
  end: string;
  excludeId?: string;
}): Promise<Change[]> {
  if (isMockMode()) {
    await delay(40);
    return [];
  }
  const qs = new URLSearchParams();
  qs.set('start', params.start);
  qs.set('end', params.end);
  if (params.excludeId) qs.set('excludeId', params.excludeId);
  const list = await apiRequest<BackendChange[]>(`/changes/conflicts?${qs}`);
  return (list ?? []).map(mapChange);
}

export async function fetchChangeConflicts(id: string): Promise<Change[]> {
  if (isMockMode()) {
    await delay(40);
    return [];
  }
  const list = await apiRequest<BackendChange[]>(
    `/changes/${encodeURIComponent(id)}/conflicts`,
  );
  return (list ?? []).map(mapChange);
}

export async function createChange(
  payload: CreateChangePayload,
): Promise<Change> {
  if (isMockMode()) {
    await delay(180);
    return storeAddChange(payload);
  }
  const implementationPlan =
    payload.implementationPlan?.trim() || payload.description?.trim() || 'TBD';
  const rollbackPlan = payload.backoutPlan?.trim() || 'TBD';
  const created = await apiRequest<BackendChange>('/changes', {
    method: 'POST',
    body: {
      type: toBackendChangeType(payload.type ?? 'normal'),
      risk: toBackendRisk(payload.risk ?? 'medium'),
      title: payload.title,
      plannedStart: toIsoOrUndefined(payload.plannedStart),
      plannedEnd: toIsoOrUndefined(payload.plannedEnd),
      implementationPlan,
      rollbackPlan,
      businessJustification: payload.description,
    },
  });
  return mapChange(created);
}

export async function transitionChangeStatus(
  id: string,
  next: ChangeStatus,
  expectedVersion = 0,
): Promise<{ ok: true; change: Change } | { ok: false; errorKey: string }> {
  if (isMockMode()) {
    await delay(120);
    return storeTransitionChange(id, next);
  }
  try {
    const dto = await apiRequest<BackendChange>(`/changes/${id}/transitions`, {
      method: 'POST',
      body: { target: toBackendChangeTarget(next), expectedVersion },
    });
    return { ok: true, change: await hydrateCabVotes(mapChange(dto)) };
  } catch {
    return { ok: false, errorKey: 'module.errors.invalidTransition' };
  }
}

export async function patchChange(
  id: string,
  patch: Parameters<typeof storeUpdateChange>[1] & { expectedVersion?: number },
): Promise<{ ok: true; change: Change } | { ok: false; errorKey: string }> {
  if (isMockMode()) {
    await delay(100);
    const mockPatch = { ...patch };
    delete mockPatch.expectedVersion;
    return storeUpdateChange(id, mockPatch);
  }
  try {
    const dto = await apiRequest<BackendChange>(`/changes/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: {
        expectedVersion: patch.expectedVersion ?? 0,
        plannedStart: patch.plannedStart,
        plannedEnd: patch.plannedEnd,
        implementationPlan: patch.implementationPlan,
        rollbackPlan: patch.backoutPlan,
        businessJustification: patch.description,
        cabNotes: patch.cabNotes,
        cabRiskLevel: patch.risk ? toBackendRisk(patch.risk) : undefined,
      },
    });
    return { ok: true, change: await hydrateCabVotes(mapChange(dto)) };
  } catch {
    return { ok: false, errorKey: 'module.errors.invalidTransition' };
  }
}

export async function setChangeCabDecision(
  id: string,
  decision: 'approve' | 'reject',
  notes?: string,
  expectedVersion = 0,
): Promise<{ ok: true; change: Change } | { ok: false; errorKey: string }> {
  if (isMockMode()) {
    await delay(120);
    return storeSetCabDecision(id, decision, notes);
  }
  try {
    // Chair decision = lifecycle transition after CAB
    const target = decision === 'approve' ? 'APPROVED' : 'REJECTED';
    const dto = await apiRequest<BackendChange>(`/changes/${id}/transitions`, {
      method: 'POST',
      body: {
        target,
        cabNotes: notes,
        expectedVersion,
      },
    });
    return { ok: true, change: await hydrateCabVotes(mapChange(dto)) };
  } catch {
    return { ok: false, errorKey: 'module.errors.invalidTransition' };
  }
}

export async function castCabMemberVote(
  id: string,
  _memberId: string,
  decision: CabVoteDecision,
): Promise<{ ok: true; change: Change } | { ok: false; errorKey: string }> {
  if (isMockMode()) {
    await delay(80);
    return storeCastCabVote(id, _memberId, decision);
  }
  try {
    await apiRequest(`/changes/${id}/votes`, {
      method: 'POST',
      body: {
        decision: decision === 'reject' ? 'REJECT' : 'APPROVE',
      },
    });
    const dto = await apiRequest<BackendChange>(`/changes/${id}`);
    return { ok: true, change: await hydrateCabVotes(mapChange(dto)) };
  } catch {
    return { ok: false, errorKey: 'module.errors.invalidTransition' };
  }
}

export async function getChangeTransitions(
  change: Pick<Change, 'id' | 'status'>,
): Promise<string[]> {
  if (isMockMode()) {
    const { getChangeTransitions: mockGet } = await import('@/mock/store');
    return mockGet(change.status);
  }
  return apiRequest<string[]>(
    `/changes/${encodeURIComponent(change.id)}/transitions`,
  );
}

export function cabChairApproveAllowed(change: Change): boolean {
  if (change.type === 'standard') return true;
  return countCabApproves(change) >= CAB_QUORUM_APPROVES;
}

export function countCabApproves(change: Change): number {
  return (change.cabVotes ?? []).filter((v) => v.decision === 'approve').length;
}

export const CAB_QUORUM_APPROVES = 2;

export async function bulkAssignChanges(ids: string[]): Promise<number> {
  if (isMockMode()) {
    await delay(80);
    return storeBulkAssignChanges(ids);
  }
  const response = await apiRequest<{ updated: number }>(
    '/changes/bulk/assign',
    { method: 'POST', body: { ids } },
  );
  return response.updated;
}

export type { BulkStatusResult };

export async function bulkSetChangeStatus(
  ids: string[],
  status: ChangeStatus,
): Promise<BulkStatusResult> {
  if (isMockMode()) {
    await delay(80);
    return storeBulkSetChangeStatus(ids, status);
  }
  const response = await apiRequest<{
    succeeded: number;
    results: Array<{ id: string; success: boolean; errorCode?: string | null }>;
  }>('/changes/bulk/transitions', {
    method: 'POST', body: { ids, target: toBackendChangeTarget(status) },
  });
  return {
    ok: response.succeeded,
    skipped: response.results.filter((item) => !item.success).map((item) => ({
      id: item.id, number: item.id, errorKey: 'module.errors.invalidTransition',
    })),
  };
}
