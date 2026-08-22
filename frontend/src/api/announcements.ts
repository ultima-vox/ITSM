import { apiRequest, delay, isMockMode } from './client';
import {
  activeMockAnnouncements,
  createMockAnnouncement,
  deleteMockAnnouncement,
  listMockAnnouncements,
  retireMockAnnouncement,
  updateMockAnnouncement,
} from '@/mock/announcements';
import type { Announcement, AnnouncementInput } from '@/types';

export async function fetchActiveAnnouncements(
  signal?: AbortSignal,
): Promise<Announcement[]> {
  if (isMockMode()) {
    await delay(70);
    return activeMockAnnouncements('AGENTS', new Date().toISOString());
  }
  return (await apiRequest<Announcement[]>('/announcements/active', { signal })) ?? [];
}

export async function fetchAnnouncements(signal?: AbortSignal): Promise<Announcement[]> {
  if (isMockMode()) {
    await delay(110);
    return listMockAnnouncements();
  }
  return (await apiRequest<Announcement[]>('/announcements', { signal })) ?? [];
}

export async function createAnnouncement(input: AnnouncementInput): Promise<Announcement> {
  if (isMockMode()) {
    await delay(140);
    return createMockAnnouncement(input);
  }
  return apiRequest<Announcement>('/announcements', { method: 'POST', body: input });
}

export async function updateAnnouncement(
  id: string,
  input: AnnouncementInput,
): Promise<Announcement> {
  if (isMockMode()) {
    await delay(140);
    return updateMockAnnouncement(id, input);
  }
  return apiRequest<Announcement>(`/announcements/${id}`, { method: 'PATCH', body: input });
}

export async function retireAnnouncement(id: string): Promise<Announcement> {
  if (isMockMode()) {
    await delay(120);
    return retireMockAnnouncement(id);
  }
  return apiRequest<Announcement>(`/announcements/${id}/retire`, { method: 'POST' });
}

export async function deleteAnnouncement(id: string): Promise<void> {
  if (isMockMode()) {
    await delay(120);
    deleteMockAnnouncement(id);
    return;
  }
  await apiRequest<void>(`/announcements/${id}`, { method: 'DELETE' });
}
