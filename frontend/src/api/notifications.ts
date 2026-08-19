import { delay, isMockMode, apiRequest, getBaseUrl, getApiToken } from './client';
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
  readAt?: string | null;
  unread?: boolean | null;
  source?: string | null;
  entityType?: string | null;
  entityId?: string | null;
}

interface BackendNotificationList {
  items?: BackendNotification[];
  unreadCount?: number;
  limit?: number;
  offset?: number;
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

function hrefFromVariables(
  vars?: Record<string, unknown> | null,
  entityType?: string | null,
  entityId?: string | null,
): string {
  if (entityId && (!entityType || entityType === 'work_item')) {
    return `/work-items/${entityId}`;
  }
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

  const unread =
    typeof dto.unread === 'boolean'
      ? dto.unread
      : dto.readAt == null || dto.readAt === '';

  return {
    id: String(dto.id),
    kind: kindFromTemplate(dto.templateKey),
    titleKey: 'notifications.liveTitle',
    bodyKey: 'notifications.liveBody',
    title,
    body,
    at: dto.createdAt ?? new Date().toISOString(),
    href: hrefFromVariables(vars, dto.entityType, dto.entityId),
    workItemId:
      dto.entityId != null
        ? String(dto.entityId)
        : vars.workItemId != null
          ? String(vars.workItemId)
          : vars.work_item_id != null
            ? String(vars.work_item_id)
            : undefined,
    unread,
  };
}

function extractList(
  payload: BackendNotification[] | BackendNotificationList | null | undefined,
): BackendNotification[] {
  if (!payload) return [];
  if (Array.isArray(payload)) return payload;
  return payload.items ?? [];
}

/**
 * List notifications. Live mode hits GET /notifications.
 * S24: failures rethrow — UI surfaces error (no silent mock seed fallback).
 */
export async function fetchNotifications(): Promise<AppNotification[]> {
  if (isMockMode()) {
    await delay(60);
    ensureNotificationCenter();
    return listMockNotifications();
  }
  const payload = await apiRequest<
    BackendNotification[] | BackendNotificationList
  >('/notifications?limit=50');
  return extractList(payload).map(mapLiveNotification);
}

/**
 * Subscribe to notification changes.
 * - Mock mode: in-memory subscriber.
 * - Live mode: SSE stream via /notifications/stream, with polling fallback.
 */
export function subscribeNotifications(listener: () => void): () => void {
  if (isMockMode()) return subscribeMock(listener);

  // Try SSE first
  const baseUrl = getBaseUrl();
  const token = getApiToken();
  const url = `${baseUrl}/notifications/stream`;
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;

  let es: EventSource | null = null;
  let fallbackInterval: ReturnType<typeof setInterval> | null = null;

  try {
    // EventSource doesn't support custom headers natively; use withCredentials for cookie auth
    // or fall back to polling if Bearer token is needed
    if (token) {
      // Bearer token auth — EventSource can't send headers, use fetch-based SSE
      const controller = new AbortController();
      fetch(url, {
        headers,
        signal: controller.signal,
      }).then((resp) => {
        const reader = resp.body?.getReader();
        if (!reader) return;
        const decoder = new TextDecoder();
        const pump = (): void => {
          reader.read().then(({ done, value }) => {
            if (done) return;
            const text = decoder.decode(value);
            // Parse SSE events from the stream
            if (text.includes('event: domain-event') || text.includes('data:')) {
              listener();
            }
            pump();
          }).catch(() => {/* stream closed */});
        };
        pump();
      }).catch(() => {/* fall through to polling */});
    } else {
      // Cookie-based auth — EventSource works
      es = new EventSource(url, { withCredentials: true });
      es.addEventListener('domain-event', () => listener());
      es.onerror = () => {
        // SSE failed — fall back to polling
        es?.close();
        es = null;
        if (!fallbackInterval) {
          fallbackInterval = setInterval(listener, 30_000);
        }
      };
    }
  } catch {
    // SSE setup failed — fall back to polling
    fallbackInterval = setInterval(listener, 30_000);
  }

  return () => {
    es?.close();
    if (fallbackInterval) clearInterval(fallbackInterval);
  };
}

/**
 * Synchronous notification list for components that need immediate data.
 * - Mock mode: reads in-memory store.
 * - Live mode: returns empty array (use async `fetchNotifications` instead).
 */
/** Stable identity: useSyncExternalStore treats a fresh array as a changed snapshot. */
const NO_NOTIFICATIONS: AppNotification[] = [];

export function listNotifications(): AppNotification[] {
  if (isMockMode()) {
    ensureNotificationCenter();
    return listMockNotifications();
  }
  return NO_NOTIFICATIONS;
}

export async function markNotificationRead(id: string): Promise<void> {
  if (isMockMode()) {
    markMockRead(id);
    return;
  }
  await apiRequest<void>(`/notifications/${encodeURIComponent(id)}/read`, {
    method: 'POST',
  });
}

export async function markAllNotificationsRead(): Promise<void> {
  if (isMockMode()) {
    markAllMockRead();
    return;
  }
  await apiRequest<{ updated: number }>('/notifications/read-all', {
    method: 'POST',
  });
}
