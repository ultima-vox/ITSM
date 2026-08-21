import { apiRequest, delay, isMockMode } from './client';
import type { QueueSavedView } from '@/types';

export interface QueueSavedViewRecord {
  id: string;
  name: string;
  tab: string;
  priority: string;
  type: string;
  status: string;
  sla: string;
}

function mapView(dto: QueueSavedViewRecord): QueueSavedView {
  return {
    id: dto.id,
    name: dto.name,
    tab: dto.tab || 'all',
    priority: dto.priority || '',
    type: dto.type || '',
    status: dto.status || '',
    sla: dto.sla || '',
  };
}

export async function fetchQueueSavedViews(): Promise<QueueSavedView[]> {
  if (isMockMode()) {
    await delay(40);
    return [];
  }
  const list = await apiRequest<QueueSavedViewRecord[]>('/me/queue-views');
  return (list ?? []).map(mapView);
}

export async function createQueueSavedView(
  view: Omit<QueueSavedView, 'id' | 'builtin'>,
): Promise<QueueSavedView> {
  if (isMockMode()) {
    throw new Error('createQueueSavedView is live-only');
  }
  const created = await apiRequest<QueueSavedViewRecord>('/me/queue-views', {
    method: 'POST',
    body: {
      name: view.name,
      tab: view.tab,
      priority: view.priority,
      type: view.type,
      status: view.status,
      sla: view.sla,
    },
  });
  return mapView(created);
}

export async function deleteQueueSavedView(id: string): Promise<void> {
  if (isMockMode()) {
    throw new Error('deleteQueueSavedView is live-only');
  }
  await apiRequest(`/me/queue-views/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
