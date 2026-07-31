import { delay, useMock, apiRequest } from './client';
import { mapProblem, type BackendProblemSummary } from './mappers/problems';
import {
  addProblem as storeAddProblem,
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

export async function fetchProblems(): Promise<Problem[]> {
  if (useMock()) {
    await delay(220);
    return listProblems();
  }
  const list = await apiRequest<BackendProblemSummary[]>('/problems');
  return (list ?? []).map(mapProblem);
}

export async function createProblem(
  payload: CreateProblemPayload,
): Promise<Problem> {
  if (useMock()) {
    await delay(180);
    return storeAddProblem(payload);
  }
  const created = await apiRequest<BackendProblemSummary>('/problems', {
    method: 'POST',
    body: payload,
  });
  return mapProblem(created);
}

export async function transitionProblemStatus(
  id: string,
  next: WorkItemStatus,
  opts?: { rootCause?: string; workaround?: string; knownError?: boolean },
): Promise<{ ok: true; problem: Problem } | { ok: false; errorKey: string }> {
  if (useMock()) {
    await delay(120);
    return storeTransitionProblem(id, next, opts);
  }
  try {
    const dto = await apiRequest<BackendProblemSummary>(
      `/problems/${id}/transition`,
      { method: 'POST', body: { status: next, ...opts } },
    );
    return { ok: true, problem: mapProblem(dto) };
  } catch {
    return { ok: false, errorKey: 'module.errors.invalidTransition' };
  }
}

export async function patchProblem(
  id: string,
  patch: Parameters<typeof storeUpdateProblem>[1],
): Promise<{ ok: true; problem: Problem } | { ok: false; errorKey: string }> {
  if (useMock()) {
    await delay(100);
    return storeUpdateProblem(id, patch);
  }
  return { ok: false, errorKey: 'module.errors.notFound' };
}

export { getProblemTransitions };
