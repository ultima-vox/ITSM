import { delay, isMockMode, apiRequest, refuseLiveFeature } from './client';
import { mapProblem, type BackendProblemSummary } from './mappers/problems';
import {
  addProblem as storeAddProblem,
  bulkAssignProblems as storeBulkAssignProblems,
  bulkSetProblemStatus as storeBulkSetProblemStatus,
  getProblemTransitions,
  listProblems,
  transitionProblem as storeTransitionProblem,
  updateProblemFields as storeUpdateProblem,
} from '@/mock/store';
import type {
  CreateProblemPayload,
  Problem,
  WorkItemStatus,
} from '@/types';

/** Map UI work-item-like status to backend Problem.Status enum. */
function toBackendProblemTarget(status: WorkItemStatus): string {
  switch (status) {
    case 'new':
      return 'NEW';
    case 'in_progress':
      return 'UNDER_INVESTIGATION';
    case 'waiting':
      return 'KNOWN_ERROR';
    case 'resolved':
      return 'RESOLVED';
    case 'closed':
    case 'cancelled':
      return 'CLOSED';
    default:
      return 'UNDER_INVESTIGATION';
  }
}

export async function fetchProblems(): Promise<Problem[]> {
  if (isMockMode()) {
    await delay(220);
    return listProblems();
  }
  const list = await apiRequest<BackendProblemSummary[]>('/problems');
  return (list ?? []).map(mapProblem);
}

export async function createProblem(
  payload: CreateProblemPayload,
): Promise<Problem> {
  if (isMockMode()) {
    await delay(180);
    return storeAddProblem(payload);
  }
  const created = await apiRequest<BackendProblemSummary>('/problems', {
    method: 'POST',
    body: {
      title: payload.title,
      rootCause: payload.rootCause,
      workaround: payload.workaround,
    },
  });
  return mapProblem(created);
}

export async function transitionProblemStatus(
  id: string,
  next: WorkItemStatus,
  opts?: {
    rootCause?: string;
    workaround?: string;
    resolution?: string;
    knownError?: boolean;
    expectedVersion?: number;
  },
): Promise<{ ok: true; problem: Problem } | { ok: false; errorKey: string }> {
  if (isMockMode()) {
    await delay(120);
    return storeTransitionProblem(id, next, opts);
  }
  try {
    const dto = await apiRequest<BackendProblemSummary>(
      `/problems/${id}/transitions`,
      {
        method: 'POST',
        body: {
          target: toBackendProblemTarget(next),
          rootCause: opts?.rootCause,
          workaround: opts?.workaround,
          resolution: opts?.resolution,
          expectedVersion: opts?.expectedVersion ?? 0,
        },
      },
    );
    return { ok: true, problem: mapProblem(dto) };
  } catch {
    return { ok: false, errorKey: 'module.errors.invalidTransition' };
  }
}

export async function patchProblem(
  id: string,
  patch: Parameters<typeof storeUpdateProblem>[1] & { expectedVersion?: number },
): Promise<{ ok: true; problem: Problem } | { ok: false; errorKey: string }> {
  if (isMockMode()) {
    await delay(100);
    return storeUpdateProblem(id, patch);
  }
  try {
    if (patch.knownError === true) {
      const dto = await apiRequest<BackendProblemSummary>(
        `/problems/${id}/transitions`,
        {
          method: 'POST',
          body: {
            target: 'KNOWN_ERROR',
            rootCause: patch.rootCause,
            workaround: patch.workaround,
            resolution: (patch as { resolution?: string }).resolution,
            expectedVersion: patch.expectedVersion ?? 0,
          },
        },
      );
      return { ok: true, problem: mapProblem(dto) };
    }
    if (patch.knownError === false) {
      const dto = await apiRequest<BackendProblemSummary>(
        `/problems/${id}/transitions`,
        {
          method: 'POST',
          body: {
            target: 'UNDER_INVESTIGATION',
            rootCause: patch.rootCause,
            workaround: patch.workaround,
            resolution: (patch as { resolution?: string }).resolution,
            expectedVersion: patch.expectedVersion ?? 0,
          },
        },
      );
      return { ok: true, problem: mapProblem(dto) };
    }
    const dto = await apiRequest<BackendProblemSummary>(`/problems/${id}`, {
      method: 'PATCH',
      body: {
        rootCause: patch.rootCause,
        workaround: patch.workaround,
        resolution: (patch as { resolution?: string }).resolution,
        expectedVersion: patch.expectedVersion ?? 0,
      },
    });
    return { ok: true, problem: mapProblem(dto) };
  } catch {
    return { ok: false, errorKey: 'module.errors.invalidTransition' };
  }
}

export { getProblemTransitions };

export async function bulkAssignProblems(ids: string[]): Promise<number> {
  if (isMockMode()) {
    await delay(80);
    return storeBulkAssignProblems(ids);
  }
  refuseLiveFeature('module.errors.bulkLiveUnsupported');
}

export async function bulkSetProblemStatus(
  ids: string[],
  status: WorkItemStatus,
): Promise<number> {
  if (isMockMode()) {
    await delay(80);
    return storeBulkSetProblemStatus(ids, status);
  }
  const response = await apiRequest<{ succeeded: number }>(
    '/problems/bulk/transitions',
    { method: 'POST', body: { ids, target: toBackendProblemTarget(status) } },
  );
  return response.succeeded;
}
