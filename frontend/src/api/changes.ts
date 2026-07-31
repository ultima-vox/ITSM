import { delay, useMock, apiRequest, refuseLiveFeature } from './client';
import { mapChange, type BackendChange } from './mappers/changes';
import {
  addChange as storeAddChange,
  bulkAssignChanges as storeBulkAssignChanges,
  bulkSetChangeStatus as storeBulkSetChangeStatus,
  type BulkStatusResult,
  cabChairApproveAllowed as storeCabChairApproveAllowed,
  castCabMemberVote as storeCastCabVote,
  countCabApproves as storeCountCabApproves,
  CAB_QUORUM_APPROVES,
  getChangeTransitions,
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
    case 'cab_review':
      return 'CAB_REVIEW';
    case 'scheduled':
      return 'SCHEDULED';
    case 'in_progress':
      return 'IMPLEMENTING';
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
  if (useMock()) {
    await delay(220);
    return listChanges();
  }
  const list = await apiRequest<BackendChange[]>('/changes');
  return (list ?? []).map(mapChange);
}

/** Live schedule overlap from backend; mock returns []. */
export async function fetchScheduleConflicts(params: {
  start: string;
  end: string;
  excludeId?: string;
}): Promise<Change[]> {
  if (useMock()) {
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
  if (useMock()) {
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
  if (useMock()) {
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
): Promise<{ ok: true; change: Change } | { ok: false; errorKey: string }> {
  if (useMock()) {
    await delay(120);
    return storeTransitionChange(id, next);
  }
  try {
    const dto = await apiRequest<BackendChange>(`/changes/${id}/transitions`, {
      method: 'POST',
      body: { target: toBackendChangeTarget(next) },
    });
    return { ok: true, change: mapChange(dto) };
  } catch {
    return { ok: false, errorKey: 'module.errors.invalidTransition' };
  }
}

export async function patchChange(
  id: string,
  patch: Parameters<typeof storeUpdateChange>[1],
): Promise<{ ok: true; change: Change } | { ok: false; errorKey: string }> {
  if (useMock()) {
    await delay(100);
    return storeUpdateChange(id, patch);
  }
  return { ok: false, errorKey: 'module.errors.notFound' };
}

export async function setChangeCabDecision(
  id: string,
  decision: 'approve' | 'reject',
  notes?: string,
): Promise<{ ok: true; change: Change } | { ok: false; errorKey: string }> {
  if (useMock()) {
    await delay(120);
    return storeSetCabDecision(id, decision, notes);
  }
  return { ok: false, errorKey: 'module.errors.notFound' };
}

export async function castCabMemberVote(
  id: string,
  memberId: string,
  decision: CabVoteDecision,
): Promise<{ ok: true; change: Change } | { ok: false; errorKey: string }> {
  if (useMock()) {
    await delay(80);
    return storeCastCabVote(id, memberId, decision);
  }
  return { ok: false, errorKey: 'module.errors.notFound' };
}

export { getChangeTransitions };

export function cabChairApproveAllowed(
  change: Parameters<typeof storeCabChairApproveAllowed>[0],
): boolean {
  return storeCabChairApproveAllowed(change);
}

export function countCabApproves(
  change: Parameters<typeof storeCountCabApproves>[0],
): number {
  return storeCountCabApproves(change);
}

export { CAB_QUORUM_APPROVES };

export async function bulkAssignChanges(ids: string[]): Promise<number> {
  if (useMock()) {
    await delay(80);
    return storeBulkAssignChanges(ids);
  }
  // No live bulk-assign endpoint — refuse (S23). Never fake ids.length.
  refuseLiveFeature('module.errors.bulkLiveUnsupported');
}

export type { BulkStatusResult };

export async function bulkSetChangeStatus(
  ids: string[],
  status: ChangeStatus,
): Promise<BulkStatusResult> {
  if (useMock()) {
    await delay(80);
    return storeBulkSetChangeStatus(ids, status);
  }
  // Live: per-item transitions (honest count of successes + skips).
  const settled = await Promise.all(
    ids.map(async (id) => {
      const r = await transitionChangeStatus(id, status);
      return { id, r };
    }),
  );
  const skipped: BulkStatusResult['skipped'] = [];
  let ok = 0;
  for (const x of settled) {
    if (x.r.ok) ok += 1;
    else
      skipped.push({
        id: x.id,
        number: x.id,
        errorKey: x.r.errorKey,
      });
  }
  return { ok, skipped };
}
