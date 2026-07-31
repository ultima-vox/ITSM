import { delay, useMock, apiRequest } from './client';
import { mapChange, type BackendChange } from './mappers/changes';
import { changes } from '@/mock/data';
import type { Change } from '@/types';

export async function fetchChanges(): Promise<Change[]> {
  if (useMock()) {
    await delay(220);
    return changes;
  }
  const list = await apiRequest<BackendChange[]>('/changes');
  return (list ?? []).map(mapChange);
}
