import { delay, useMock, apiRequest } from './client';
import {
  ensureNotificationCenter,
  listNotifications as listMockNotifications,
  markAllNotificationsRead as markAllMockRead,
  markNotificationRead as markMockRead,
  subscribeNotifications as subscribeMock,
  type AppNotification,
  type NotificationKind,
} from '@/mock/notifications';

export type { AppNotification, NotificationKind };

interface BackendNotification {
  id: string;
  createdAt: string;
  correlationId?: string | null;
  templateKey?: string | null;
  recipientSubject?: string | null;
  locale?: string | null;
  variables?: Record<string, unknown> | null;
  channel?: string | null;
}

function kindFromTemplate(templateKey?: string | null): NotificationKind {
  const key = (templateKey ?? '').toLowerCase();
  if (key.includes('breach')) return 'breach';
  if (key.includes('sla') || key.includes('at_risk') || key.includes('risk')) {
    return 'sla';
  }
  if (key.includes('assign')) return 'assign';
  if (key.includes('mention')) return 'mention';
  return 'mention';
}

function hrefFromVariables(vars?: Record<string, unknown> | null): string {
  if (!vars) return '/my-work';
  const wi =
    vars.workItemId ??
    vars.work_item_id ??
    vars.entityId ??
    vars.correlationId;
  if (wi != null && String(wi).trim()) {
    return `/work-items/${String(wi)}`;
  }
  return '/my-work';
}

function mapLiveNotification(dto: BackendNotification): AppNotification {
  const vars = dto.variables ?? {};
  const title =
    (typeof vars.title === 'string' && vars.title) ||
    (typeof vars.subject === 'string' && vars.subject) ||
    dto.templateKey ||
    'Notification';
  const bodyParts: string[] = [];
  if (typeof vars.number === 'string' || typeof vars.number === 'number') {
    bodyParts.push(String(vars.number));
  }
  if (typeof vars.body === 'string') bodyParts.push(vars.body);
  else if (typeof vars.message === 'string') bodyParts.push(vars.message);
  else if (typeof vars.title === 'string' && vars.title !== title) {
    bodyParts.push(vars.title);
  }
  const body =
    bodyParts.filter(Boolean).join(' · ') ||
    dto.recipientSubject ||
    dto.templateKey ||
    '';

  return {
    id: String(dto.id),
    kind: kindFromTemplate(dto.templateKey),
    titleKey: 'notifications.liveTitle',
    bodyKey: 'notifications.liveBody',
    title,
    body,
    at: dto.createdAt ?? new Date().toISOString(),
    href: hrefFromVariables(vars),
    workItemId:
      vars.workItemId != null
        ? String(vars.workItemId)
        : vars.work_item_id != null
          ? String(vars.work_item_id)
          : undefined,
    unread: true,
  };
}

/**
 * List notifications. Live mode hits GET /notifications.
 * S24: failures rethrow — UI surfaces error (no silent mock seed fallback).
 */
export async function fetchNotifications(): Promise<AppNotification[]> {
  if (useMock()) {
    await delay(60);
    ensureNotificationCenter();
    return listMockNotifications();
  }
  const list = await apiRequest<BackendNotification[]>('/notifications');
  return (list ?? []).map(mapLiveNotification);
}

export function subscribeNotifications(listener: () => void): () => void {
  return subscribeMock(listener);
}

export function listNotifications(): AppNotification[] {
  ensureNotificationCenter();
  return listMockNotifications();
}

export function markNotificationRead(id: string): void {
  markMockRead(id);
}

export function markAllNotificationsRead(): void {
  markAllMockRead();
}
