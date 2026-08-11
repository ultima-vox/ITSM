import type { NotificationPrefs } from '@/types';
import { apiRequest, isMockMode } from './client';
import { loadNotificationPrefs } from '@/lib/notificationPrefs';

export async function fetchNotificationPreferences(): Promise<NotificationPrefs> {
  if (isMockMode()) return loadNotificationPrefs();
  return apiRequest<NotificationPrefs>('/me/notification-preferences');
}

export async function updateNotificationPreferences(
  preferences: NotificationPrefs,
): Promise<NotificationPrefs> {
  if (isMockMode()) return preferences;
  return apiRequest<NotificationPrefs>('/me/notification-preferences', {
    method: 'PUT', body: preferences,
  });
}
