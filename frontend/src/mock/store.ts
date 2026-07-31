/**
 * In-memory mutable mock store so Queues / My Work / Overview / Detail
 * stay consistent after assign, priority, escalate, resolve, comments, fields.
 */
import type {
  ImpactLevel,
  Person,
  Priority,
  UrgencyLevel,
  WorkItem,
  WorkItemActivity,
  WorkItemComment,
  WorkItemStatus,
} from '@/types';
import {
  workItems as seedItems,
  activities as seedActivities,
  comments as seedComments,
  currentUser,
  people,
  TEAMS,
} from './data';

type Listener = () => void;

function cloneItem(w: WorkItem): WorkItem {
  return {
    ...w,
    assignee: w.assignee ? { ...w.assignee } : null,
    requester: { ...w.requester },
    tags: w.tags ? [...w.tags] : undefined,
    relatedIds: w.relatedIds ? [...w.relatedIds] : undefined,
    ciIds: w.ciIds ? [...w.ciIds] : undefined,
    watchers: w.watchers?.map((p) => ({ ...p })),
    childTasks: w.childTasks?.map((c) => ({
      ...c,
      assignee: c.assignee ? { ...c.assignee } : null,
    })),
  };
}

let items: WorkItem[] = seedItems.map(cloneItem);

const activities: Record<string, WorkItemActivity[]> = Object.fromEntries(
  Object.entries(seedActivities).map(([k, list]) => [
    k,
    list.map((a) => ({ ...a, actor: { ...a.actor } })),
  ]),
);

const commentsStore: Record<string, WorkItemComment[]> = Object.fromEntries(
  Object.entries(seedComments).map(([k, list]) => [
    k,
    list.map((c) => ({ ...c, author: { ...c.author } })),
  ]),
);

const listeners = new Set<Listener>();

function nowIso() {
  return new Date().toISOString();
}

function notify() {
  listeners.forEach((fn) => fn());
}

function actorFromCurrent(): Person {
  return {
    id: currentUser.id,
    name: currentUser.name,
    initials: currentUser.initials,
    role: currentUser.role,
    teamId: currentUser.teamId,
  };
}

function pushActivity(
  id: string,
  kind: WorkItemActivity['kind'],
  text: string,
  actor: Person = actorFromCurrent(),
) {
  const entry: WorkItemActivity = {
    id: `act-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    at: nowIso(),
    actor: { ...actor },
    kind,
    text,
  };
  // Normalize lookup key to item id
  const item = items.find((i) => i.id === id || i.number === id);
  const key = item?.id ?? id;
  activities[key] = [entry, ...(activities[key] ?? [])];
}

export function subscribeWorkItems(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function listWorkItems(params?: {
  assigneeId?: string;
  queue?: string;
  q?: string;
}): WorkItem[] {
  let list = items.map(cloneItem);
  if (params?.assigneeId) {
    list = list.filter((w) => w.assignee?.id === params.assigneeId);
  }
  if (params?.queue) {
    list = list.filter((w) => w.queue === params.queue);
  }
  if (params?.q) {
    const q = params.q.toLowerCase();
    list = list.filter(
      (w) =>
        w.number.toLowerCase().includes(q) ||
        w.title.toLowerCase().includes(q) ||
        w.service.toLowerCase().includes(q),
    );
  }
  return list;
}

export function getWorkItem(id: string): WorkItem | null {
  const w = items.find((i) => i.id === id || i.number === id);
  if (!w) return null;
  return cloneItem(w);
}

export function getActivities(id: string): WorkItemActivity[] {
  const item = items.find((i) => i.id === id || i.number === id);
  const key = item?.id ?? id;
  return (activities[key] ?? []).map((a) => ({ ...a, actor: { ...a.actor } }));
}

export function getComments(id: string): WorkItemComment[] {
  const item = items.find((i) => i.id === id || i.number === id);
  const key = item?.id ?? id;
  return (commentsStore[key] ?? []).map((c) => ({
    ...c,
    author: { ...c.author },
  }));
}

export function addWorkItem(item: WorkItem): WorkItem {
  const next = cloneItem({
    ...item,
    impact: item.impact ?? 'medium',
    urgency: item.urgency ?? 'medium',
    teamId: item.teamId ?? TEAMS.sd,
    watchers: item.watchers ?? [],
    childTasks: item.childTasks ?? [],
  });
  items = [next, ...items];
  pushActivity(next.id, 'system', 'work_item_created', people.system);
  notify();
  return getWorkItem(next.id)!;
}

export function assignWorkItems(
  ids: string[],
  assignee: Person = actorFromCurrent(),
): void {
  const set = new Set(ids);
  items = items.map((w) => {
    if (!set.has(w.id) && !set.has(w.number)) return w;
    const status: WorkItemStatus =
      w.status === 'new' ? 'in_progress' : w.status;
    return {
      ...w,
      assignee: { ...assignee },
      status,
      teamId: assignee.teamId ?? w.teamId,
      updatedAt: nowIso(),
    };
  });
  ids.forEach((id) => pushActivity(id, 'assignment', 'assigned_to_me'));
  notify();
}

export function setWorkItemPriority(ids: string[], priority: Priority): void {
  const set = new Set(ids);
  items = items.map((w) => {
    if (!set.has(w.id) && !set.has(w.number)) return w;
    return {
      ...w,
      priority,
      updatedAt: nowIso(),
    };
  });
  ids.forEach((id) => pushActivity(id, 'field', 'priority_changed'));
  notify();
}

export function escalateWorkItem(id: string): WorkItem | null {
  items = items.map((w) => {
    if (w.id !== id && w.number !== id) return w;
    const tags = new Set(w.tags ?? []);
    tags.add('escalated');
    return {
      ...w,
      priority: w.priority === 'critical' ? 'critical' : 'high',
      status: w.status === 'new' ? 'in_progress' : w.status,
      escalated: true,
      tags: [...tags],
      impact: w.impact === 'low' ? 'medium' : w.impact ?? 'high',
      urgency: 'high',
      updatedAt: nowIso(),
    };
  });
  pushActivity(id, 'status', 'escalated');
  notify();
  return getWorkItem(id);
}

export function resolveWorkItem(
  id: string,
  resolutionNotes?: string,
): WorkItem | null {
  items = items.map((w) => {
    if (w.id !== id && w.number !== id) return w;
    return {
      ...w,
      status: 'resolved',
      slaState: w.slaState === 'breached' ? 'breached' : 'met',
      resolutionNotes: resolutionNotes?.trim() || w.resolutionNotes,
      updatedAt: nowIso(),
    };
  });
  pushActivity(id, 'status', 'status_resolved');
  notify();
  return getWorkItem(id);
}

export type WorkItemPatch = Partial<
  Pick<
    WorkItem,
    | 'service'
    | 'priority'
    | 'status'
    | 'queue'
    | 'title'
    | 'description'
    | 'impact'
    | 'urgency'
    | 'teamId'
    | 'assignee'
    | 'resolutionNotes'
    | 'watchers'
  >
>;

export function updateWorkItem(id: string, patch: WorkItemPatch): WorkItem | null {
  const before = items.find((i) => i.id === id || i.number === id);
  items = items.map((w) => {
    if (w.id !== id && w.number !== id) return w;
    return {
      ...w,
      ...patch,
      assignee:
        patch.assignee === undefined
          ? w.assignee
          : patch.assignee
            ? { ...patch.assignee }
            : null,
      watchers: patch.watchers
        ? patch.watchers.map((p) => ({ ...p }))
        : w.watchers,
      updatedAt: nowIso(),
    };
  });
  if (before) {
    if (patch.impact && patch.impact !== before.impact) {
      pushActivity(id, 'field', 'impact_changed');
    }
    if (patch.urgency && patch.urgency !== before.urgency) {
      pushActivity(id, 'field', 'urgency_changed');
    }
    if (patch.service && patch.service !== before.service) {
      pushActivity(id, 'field', 'service_changed');
    }
    if (patch.status && patch.status !== before.status) {
      pushActivity(
        id,
        'status',
        patch.status === 'waiting'
          ? 'status_pending_info'
          : patch.status === 'resolved'
            ? 'status_resolved'
            : 'status_changed_in_progress',
      );
    }
    if (patch.priority && patch.priority !== before.priority) {
      pushActivity(id, 'field', 'priority_changed');
    }
  }
  notify();
  return getWorkItem(id);
}

export function addComment(
  id: string,
  body: string,
  opts?: { internal?: boolean },
): WorkItemComment | null {
  const item = items.find((i) => i.id === id || i.number === id);
  if (!item) return null;
  const entry: WorkItemComment = {
    id: `c-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    at: nowIso(),
    author: actorFromCurrent(),
    body: body.trim(),
    internal: opts?.internal ?? true,
  };
  commentsStore[item.id] = [entry, ...(commentsStore[item.id] ?? [])];
  pushActivity(item.id, 'comment', 'comment_added');
  items = items.map((w) =>
    w.id === item.id ? { ...w, updatedAt: nowIso() } : w,
  );
  notify();
  return { ...entry, author: { ...entry.author } };
}

export function addWatcher(id: string, person: Person = actorFromCurrent()): WorkItem | null {
  items = items.map((w) => {
    if (w.id !== id && w.number !== id) return w;
    const watchers = w.watchers ?? [];
    if (watchers.some((p) => p.id === person.id)) return w;
    return {
      ...w,
      watchers: [...watchers, { ...person }],
      updatedAt: nowIso(),
    };
  });
  pushActivity(id, 'system', 'watcher_added');
  notify();
  return getWorkItem(id);
}

export function removeWatcher(id: string, personId: string): WorkItem | null {
  items = items.map((w) => {
    if (w.id !== id && w.number !== id) return w;
    return {
      ...w,
      watchers: (w.watchers ?? []).filter((p) => p.id !== personId),
      updatedAt: nowIso(),
    };
  });
  notify();
  return getWorkItem(id);
}

/** Queue predicates — single source of truth for tabs + counts */
export function isUnassigned(w: WorkItem): boolean {
  return !w.assignee;
}

export function isMyGroup(
  w: WorkItem,
  teamId: string = currentUser.teamId,
): boolean {
  return w.teamId === teamId;
}

export function isEscalated(w: WorkItem): boolean {
  return (
    w.escalated === true ||
    w.priority === 'critical' ||
    (w.tags?.includes('escalated') ?? false)
  );
}

export function isBreached(w: WorkItem): boolean {
  return w.slaState === 'breached';
}

export function countMyOpenAssigned(): number {
  return items.filter(
    (w) =>
      w.assignee?.id === currentUser.id &&
      w.status !== 'resolved' &&
      w.status !== 'closed' &&
      w.status !== 'cancelled',
  ).length;
}

export type { ImpactLevel, UrgencyLevel };
