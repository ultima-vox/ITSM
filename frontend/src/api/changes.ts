import { delay, useMock, apiRequest } from './client';
import { mapChange, type BackendChange } from './mappers/changes';
import {
  addChange as storeAddChange,
  getChangeTransitions,
  listChanges,
  transitionChange as storeTransitionChange,
  updateChangeFields as storeUpdateChange,
} from '@/mock/store';
import type {
  Change,
  ChangeStatus,
  CreateChangePayload,
} from '@/types';

export async function fetchChanges(): Promise<Change[]> {
  if (useMock()) {
    await delay(220);
    return listChanges();
  }
  const list = await apiRequest<BackendChange[]>('/changes');
  return (list ?? []).map(mapChange);
}

export async function createChange(
  payload: CreateChangePayload,
): Promise<Change> {
  if (useMock()) {
    await delay(180);
    return storeAddChange(payload);
  }
  const created = await apiRequest<BackendChange>('/changes', {
    method: 'POST',
    body: payload,
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
    const dto = await apiRequest<BackendChange>(`/changes/${id}/transition`, {
      method: 'POST',
      body: { status: next },
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

export { getChangeTransitions };
