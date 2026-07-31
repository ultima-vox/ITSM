/**
 * In-app notification center backed by seed data + work-item store events.
 * Read state persists in localStorage.
 */
import { listWorkItems, subscribeWorkItems } from './store';
import type { SlaState } from '@/types';

export type NotificationKind = 'sla' | 'assign' | 'mention' | 'breach';

export interface AppNotification {
  id: string;
  kind: NotificationKind;
  /** i18n title key */
  titleKey: string;
  /** i18n body key; may use vars */
  bodyKey: string;
  bodyVars?: Record<string, string | number>;
  titleVars?: Record<string, string | number>;
  /** Plain title from live API (bypasses i18n when set) */
  title?: string;
  /** Plain body from live API (bypasses i18n when set) */
  body?: string;
  /** ISO timestamp */
  at: string;
  /** Navigate target */
  href: string;
  workItemId?: string;
  unread: boolean;
}

const READ_KEY = 'vox-notif-read';
const SEED_IDS = ['n1', 'n2', 'n3', 'n4'] as const;

type Snapshot = {
  id: string;
  number: string;
  title: string;
  assigneeId: string | null;
  slaState: SlaState;
};

let items: AppNotification[] = [];
/** Cached list reference for useSyncExternalStore — must be stable until data changes. */
let listCache: AppNotification[] = [];
let snapshot = new Map<string, Snapshot>();
let started = false;
const listeners = new Set<() => void>();

function nowIso() {
  return new Date().toISOString();
}

function minutesAgo(m: number): string {
  return new Date(Date.now() - m * 60_000).toISOString();
}

function loadReadIds(): Set<string> {
  try {
    const raw = localStorage.getItem(READ_KEY);
    if (!raw) return new Set();
    const parsed = JSON.parse(raw) as string[];
    return new Set(Array.isArray(parsed) ? parsed : []);
  } catch {
    return new Set();
  }
}

function saveReadIds(ids: Set<string>) {
  try {
    localStorage.setItem(READ_KEY, JSON.stringify([...ids]));
  } catch {
    /* ignore quota */
  }
}

function seedNotifications(): AppNotification[] {
  const read = loadReadIds();
  const seeds: AppNotification[] = [
    {
      id: 'n1',
      kind: 'sla',
      titleKey: 'notifications.slaRiskTitle',
      bodyKey: 'notifications.slaRiskBody',
      at: minutesAgo(8),
      href: '/work-items/wi-1842',
      workItemId: 'wi-1842',
      unread: !read.has('n1'),
    },
    {
      id: 'n2',
      kind: 'assign',
      titleKey: 'notifications.assignTitle',
      bodyKey: 'notifications.assignBody',
      at: minutesAgo(22),
      href: '/work-items/wi-1838',
      workItemId: 'wi-1838',
      unread: !read.has('n2'),
    },
    {
      id: 'n3',
      kind: 'mention',
      titleKey: 'notifications.mentionTitle',
      bodyKey: 'notifications.mentionBody',
      at: minutesAgo(60),
      href: '/work-items/wi-1842',
      workItemId: 'wi-1842',
      unread: !read.has('n3'),
    },
    {
      id: 'n4',
      kind: 'sla',
      titleKey: 'notifications.resolvedTitle',
      bodyKey: 'notifications.resolvedBody',
      at: minutesAgo(180),
      href: '/work-items/wi-1820',
      workItemId: 'wi-1820',
      unread: !read.has('n4'),
    },
  ];
  return seeds;
}

function takeSnapshot(): Map<string, Snapshot> {
  const map = new Map<string, Snapshot>();
  for (const w of listWorkItems()) {
    map.set(w.id, {
      id: w.id,
      number: w.number,
      title: w.title,
      assigneeId: w.assignee?.id ?? null,
      slaState: w.slaState,
    });
  }
  return map;
}

function rebuildListCache() {
  // Shallow copy only when notifying; getSnapshot must return same ref between emits.
  listCache = items;
}

function pushNotification(n: Omit<AppNotification, 'unread'>) {
  // de-dupe by id
  if (items.some((x) => x.id === n.id)) return;
  const read = loadReadIds();
  items = [{ ...n, unread: !read.has(n.id) }, ...items].slice(0, 40);
  rebuildListCache();
  emit();
}

function diffAndNotify(prev: Map<string, Snapshot>, next: Map<string, Snapshot>) {
  for (const [id, cur] of next) {
    const before = prev.get(id);
    if (!before) continue;

    // Assignment change → notify
    if (before.assigneeId !== cur.assigneeId && cur.assigneeId) {
      pushNotification({
        id: `assign-${id}-${cur.assigneeId}-${Date.now()}`,
        kind: 'assign',
        titleKey: 'notifications.assignTitle',
        bodyKey: 'notifications.eventBody',
        bodyVars: { number: cur.number, title: cur.title },
        at: nowIso(),
        href: `/work-items/${id}`,
        workItemId: id,
      });
    }

    // SLA state transitions
    if (before.slaState !== cur.slaState) {
      if (cur.slaState === 'breached') {
        pushNotification({
          id: `breach-${id}-${Date.now()}`,
          kind: 'breach',
          titleKey: 'notifications.breachTitle',
          bodyKey: 'notifications.eventBody',
          bodyVars: { number: cur.number, title: cur.title },
          at: nowIso(),
          href: `/work-items/${id}`,
          workItemId: id,
        });
      } else if (cur.slaState === 'at_risk') {
        pushNotification({
          id: `sla-${id}-${Date.now()}`,
          kind: 'sla',
          titleKey: 'notifications.slaRiskTitle',
          bodyKey: 'notifications.eventBody',
          bodyVars: { number: cur.number, title: cur.title },
          at: nowIso(),
          href: `/work-items/${id}`,
          workItemId: id,
        });
      } else if (cur.slaState === 'met' && before.slaState !== 'met') {
        pushNotification({
          id: `resolved-${id}-${Date.now()}`,
          kind: 'sla',
          titleKey: 'notifications.resolvedTitle',
          bodyKey: 'notifications.eventBody',
          bodyVars: { number: cur.number, title: cur.title },
          at: nowIso(),
          href: `/work-items/${id}`,
          workItemId: id,
        });
      }
    }
  }
}

function emit() {
  listeners.forEach((fn) => fn());
}

/** Ensure seed + store subscription are initialized once. */
export function ensureNotificationCenter(): void {
  if (started) return;
  started = true;
  items = seedNotifications();
  rebuildListCache();
  snapshot = takeSnapshot();
  subscribeWorkItems(() => {
    const next = takeSnapshot();
    diffAndNotify(snapshot, next);
    snapshot = next;
  });
}

export function subscribeNotifications(listener: () => void): () => void {
  ensureNotificationCenter();
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function listNotifications(): AppNotification[] {
  ensureNotificationCenter();
  return listCache;
}

export function markNotificationRead(id: string): void {
  ensureNotificationCenter();
  const read = loadReadIds();
  read.add(id);
  // Keep seed ids durable; dynamic ids too
  saveReadIds(read);
  items = items.map((n) => (n.id === id ? { ...n, unread: false } : n));
  rebuildListCache();
  emit();
}

export function markAllNotificationsRead(): void {
  ensureNotificationCenter();
  const read = loadReadIds();
  for (const n of items) read.add(n.id);
  // also persist known seeds so reload stays clean
  for (const id of SEED_IDS) read.add(id);
  saveReadIds(read);
  items = items.map((n) => ({ ...n, unread: false }));
  rebuildListCache();
  emit();
}

export function unreadNotificationCount(): number {
  ensureNotificationCenter();
  return items.filter((n) => n.unread).length;
}
