/**
 * Shared notification preference store (localStorage).
 * Settings writes; NotificationMenu / center filter kinds by prefs (S11/S17).
 */
import type { NotificationPrefs } from '@/types';
import type { NotificationKind } from '@/mock/notifications';

export const NOTIF_PREFS_KEY = 'vox-notification-prefs';

export const DEFAULT_NOTIFICATION_PREFS: NotificationPrefs = {
  email: true,
  desktop: false,
  slaAlerts: true,
  assignment: true,
  mentions: true,
};

type Listener = () => void;
const listeners = new Set<Listener>();

function notify() {
  listeners.forEach((fn) => fn());
}

export function subscribeNotificationPrefs(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function loadNotificationPrefs(): NotificationPrefs {
  try {
    const raw = localStorage.getItem(NOTIF_PREFS_KEY);
    if (!raw) return { ...DEFAULT_NOTIFICATION_PREFS };
    const parsed = JSON.parse(raw) as Partial<NotificationPrefs>;
    return {
      email:
        typeof parsed.email === 'boolean'
          ? parsed.email
          : DEFAULT_NOTIFICATION_PREFS.email,
      desktop:
        typeof parsed.desktop === 'boolean'
          ? parsed.desktop
          : DEFAULT_NOTIFICATION_PREFS.desktop,
      slaAlerts:
        typeof parsed.slaAlerts === 'boolean'
          ? parsed.slaAlerts
          : DEFAULT_NOTIFICATION_PREFS.slaAlerts,
      assignment:
        typeof parsed.assignment === 'boolean'
          ? parsed.assignment
          : DEFAULT_NOTIFICATION_PREFS.assignment,
      mentions:
        typeof parsed.mentions === 'boolean'
          ? parsed.mentions
          : DEFAULT_NOTIFICATION_PREFS.mentions,
    };
  } catch {
    return { ...DEFAULT_NOTIFICATION_PREFS };
  }
}

export function saveNotificationPrefs(prefs: NotificationPrefs): void {
  try {
    localStorage.setItem(NOTIF_PREFS_KEY, JSON.stringify(prefs));
  } catch {
    /* ignore quota */
  }
  notify();
}

export function setNotificationPref<K extends keyof NotificationPrefs>(
  key: K,
  value: NotificationPrefs[K],
): NotificationPrefs {
  const next = { ...loadNotificationPrefs(), [key]: value };
  saveNotificationPrefs(next);
  return next;
}

/** Whether a notification kind is allowed by operator prefs. */
export function isNotifKindEnabled(
  kind: NotificationKind,
  prefs: NotificationPrefs = loadNotificationPrefs(),
): boolean {
  switch (kind) {
    case 'sla':
    case 'breach':
      return prefs.slaAlerts;
    case 'assign':
      return prefs.assignment;
    case 'mention':
      return prefs.mentions;
    default:
      return true;
  }
}

export function filterNotificationsByPrefs<T extends { kind: NotificationKind }>(
  items: T[],
  prefs: NotificationPrefs = loadNotificationPrefs(),
): T[] {
  return items.filter((n) => isNotifKindEnabled(n.kind, prefs));
}
