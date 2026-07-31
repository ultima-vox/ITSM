/**
 * In-memory mutable mock store so Queues / My Work / Overview / Detail
 * stay consistent after assign, priority, escalate, resolve, comments, fields.
 */
import type {
  Asset,
  AssetStatus,
  Change,
  ChangeStatus,
  CiStatus,
  ConfigurationItem,
  CreateAssetPayload,
  CreateChangePayload,
  CreateProblemPayload,
  ImpactLevel,
  ModuleActivity,
  Person,
  Priority,
  Problem,
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
  configurationItems as seedCis,
  assets as seedAssets,
  problems as seedProblems,
  changes as seedChanges,
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

/* ── CMDB configuration items (session-mutable) ─────────── */

function cloneCi(c: ConfigurationItem): ConfigurationItem {
  return { ...c };
}

let cis: ConfigurationItem[] = seedCis.map(cloneCi);
const ciListeners = new Set<Listener>();

function notifyCis() {
  ciListeners.forEach((fn) => fn());
}

export function subscribeConfigurationItems(listener: Listener): () => void {
  ciListeners.add(listener);
  return () => {
    ciListeners.delete(listener);
  };
}

export function listConfigurationItems(): ConfigurationItem[] {
  return cis.map(cloneCi);
}

export function getConfigurationItem(id: string): ConfigurationItem | null {
  const found = cis.find((c) => c.id === id);
  return found ? cloneCi(found) : null;
}

export function addConfigurationItem(input: {
  name: string;
  kindKey: string;
  status: CiStatus;
  owner?: string;
  icon?: ConfigurationItem['icon'];
  tone?: ConfigurationItem['tone'];
}): ConfigurationItem {
  const icon =
    input.icon ??
    (input.kindKey.includes('network')
      ? 'network'
      : input.kindKey.includes('database')
        ? 'database'
        : input.kindKey.includes('business')
          ? 'cloud'
          : input.kindKey.includes('application')
            ? 'app'
            : 'server');
  const tone: ConfigurationItem['tone'] =
    input.tone ??
    (icon === 'network' ? 'amber' : icon === 'cloud' ? 'cyan' : 'violet');
  const item: ConfigurationItem = {
    id: `ci-${Date.now().toString(36)}`,
    name: input.name.trim(),
    kindKey: input.kindKey,
    status: input.status,
    owner: input.owner?.trim() || currentUser.team || 'Service Desk',
    icon,
    tone,
    environment: 'production',
    criticality: 'medium',
  };
  cis = [item, ...cis];
  notifyCis();
  return cloneCi(item);
}

// ── Secondary modules: Assets / Problems / Changes ───────────────────

function cloneAsset(a: Asset): Asset {
  return {
    ...a,
    relatedCiIds: a.relatedCiIds ? [...a.relatedCiIds] : undefined,
  };
}

function cloneProblem(p: Problem): Problem {
  return {
    ...p,
    assignee: p.assignee ? { ...p.assignee } : null,
    relatedWorkItemIds: p.relatedWorkItemIds
      ? [...p.relatedWorkItemIds]
      : undefined,
    relatedCiIds: p.relatedCiIds ? [...p.relatedCiIds] : undefined,
  };
}

function cloneChange(c: Change): Change {
  return {
    ...c,
    assignee: c.assignee ? { ...c.assignee } : null,
    relatedWorkItemIds: c.relatedWorkItemIds
      ? [...c.relatedWorkItemIds]
      : undefined,
    relatedCiIds: c.relatedCiIds ? [...c.relatedCiIds] : undefined,
  };
}

function seedModuleActivities(): Record<string, ModuleActivity[]> {
  const anna = people.anna;
  const alexey = people.alexey;
  const maria = people.maria;
  const dmitry = people.dmitry;
  const system = people.system;
  return {
    'as-1001': [
      {
        id: 'ma-as1',
        at: '2026-07-28T10:00:00Z',
        actor: anna,
        kind: 'field',
        textKey: 'module.activity.assigned',
        detail: 'Анна Яковлева',
      },
      {
        id: 'ma-as2',
        at: '2025-03-12T09:00:00Z',
        actor: system,
        kind: 'system',
        textKey: 'module.activity.created',
      },
    ],
    'as-0881': [
      {
        id: 'ma-as3',
        at: '2026-01-20T11:00:00Z',
        actor: system,
        kind: 'system',
        textKey: 'module.activity.stocked',
      },
    ],
    'as-5510': [
      {
        id: 'ma-as4',
        at: '2026-07-29T14:00:00Z',
        actor: anna,
        kind: 'status',
        textKey: 'module.activity.asset_repair',
      },
    ],
    'pr-88': [
      {
        id: 'ma-pr1',
        at: '2026-07-30T08:00:00Z',
        actor: alexey,
        kind: 'field',
        textKey: 'module.activity.known_error_set',
      },
      {
        id: 'ma-pr2',
        at: '2026-07-29T12:00:00Z',
        actor: alexey,
        kind: 'status',
        textKey: 'module.activity.problem_in_progress',
      },
      {
        id: 'ma-pr3',
        at: '2026-07-28T09:00:00Z',
        actor: system,
        kind: 'system',
        textKey: 'module.activity.created',
      },
    ],
    'pr-76': [
      {
        id: 'ma-pr4b',
        at: '2026-07-29T19:10:00Z',
        actor: anna,
        kind: 'field',
        textKey: 'module.activity.fields_updated',
        detail: 'CDN cache hit ratio note',
      },
      {
        id: 'ma-pr4',
        at: '2026-07-29T18:00:00Z',
        actor: system,
        kind: 'system',
        textKey: 'module.activity.created',
      },
    ],
    'pr-61': [
      {
        id: 'ma-pr5',
        at: '2026-07-28T12:00:00Z',
        actor: maria,
        kind: 'status',
        textKey: 'module.activity.problem_resolved',
      },
      {
        id: 'ma-pr5b',
        at: '2026-07-27T16:30:00Z',
        actor: maria,
        kind: 'field',
        textKey: 'module.activity.known_error_set',
      },
      {
        id: 'ma-pr6',
        at: '2026-07-26T10:00:00Z',
        actor: maria,
        kind: 'field',
        textKey: 'module.activity.root_cause_set',
      },
      {
        id: 'ma-pr6b',
        at: '2026-07-24T08:00:00Z',
        actor: system,
        kind: 'system',
        textKey: 'module.activity.created',
      },
    ],
    'ch-422': [
      {
        id: 'ma-ch1',
        at: '2026-07-30T07:50:00Z',
        actor: dmitry,
        kind: 'status',
        textKey: 'module.activity.submitted_cab',
      },
      {
        id: 'ma-ch2',
        at: '2026-07-28T11:00:00Z',
        actor: dmitry,
        kind: 'system',
        textKey: 'module.activity.created',
      },
    ],
    'ch-418': [
      {
        id: 'ma-ch3',
        at: '2026-07-30T08:40:00Z',
        actor: alexey,
        kind: 'status',
        textKey: 'module.activity.change_in_progress',
      },
      {
        id: 'ma-ch4',
        at: '2026-07-30T07:00:00Z',
        actor: alexey,
        kind: 'status',
        textKey: 'module.activity.cab_approved',
      },
    ],
    'ch-401': [
      {
        id: 'ma-ch5',
        at: '2026-07-27T15:00:00Z',
        actor: people.olga,
        kind: 'status',
        textKey: 'module.activity.scheduled',
      },
    ],
  };
}

let assetItems: Asset[] = seedAssets.map(cloneAsset);
let problemItems: Problem[] = seedProblems.map(cloneProblem);
let changeItems: Change[] = seedChanges.map(cloneChange);
const moduleActivities: Record<string, ModuleActivity[]> = seedModuleActivities();

const secondaryListeners = new Set<Listener>();

function notifySecondary() {
  secondaryListeners.forEach((fn) => fn());
}

export function subscribeSecondaryModules(listener: Listener): () => void {
  secondaryListeners.add(listener);
  return () => {
    secondaryListeners.delete(listener);
  };
}

function pushModuleActivity(
  id: string,
  kind: ModuleActivity['kind'],
  textKey: string,
  detail?: string,
  actor: Person = actorFromCurrent(),
) {
  const entry: ModuleActivity = {
    id: `ma-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    at: nowIso(),
    actor: { ...actor },
    kind,
    textKey,
    detail,
  };
  moduleActivities[id] = [entry, ...(moduleActivities[id] ?? [])];
}

export function getModuleActivities(id: string): ModuleActivity[] {
  return (moduleActivities[id] ?? []).map((a) => ({
    ...a,
    actor: { ...a.actor },
  }));
}

// ── Assets ───────────────────────────────────────────────────────────

export function listAssets(): Asset[] {
  return assetItems.map(cloneAsset);
}

export function getAsset(id: string): Asset | null {
  const a = assetItems.find((x) => x.id === id || x.tag === id);
  return a ? cloneAsset(a) : null;
}

export function addAsset(payload: CreateAssetPayload): Asset {
  const id = `as-${Date.now().toString(36)}`;
  const next: Asset = {
    id,
    tag: payload.tag.trim(),
    name: payload.name.trim(),
    typeKey: payload.typeKey,
    status: payload.status ?? 'stock',
    assignedTo: payload.assignedTo ?? null,
    location: payload.location.trim(),
    purchasedAt: nowIso().slice(0, 10),
    serial: payload.serial?.trim() || undefined,
    model: payload.model?.trim() || undefined,
    vendor: payload.vendor?.trim() || undefined,
    notes: payload.notes?.trim() || undefined,
    updatedAt: nowIso(),
  };
  assetItems = [next, ...assetItems];
  pushModuleActivity(id, 'system', 'module.activity.created');
  notifySecondary();
  return cloneAsset(next);
}

const ASSET_TRANSITIONS: Record<AssetStatus, AssetStatus[]> = {
  stock: ['in_use', 'repair', 'retired'],
  in_use: ['stock', 'repair', 'retired'],
  repair: ['in_use', 'stock', 'retired'],
  retired: [],
};

export function getAssetTransitions(status: AssetStatus): AssetStatus[] {
  return ASSET_TRANSITIONS[status] ?? [];
}

export function transitionAsset(
  id: string,
  next: AssetStatus,
  opts?: { assignedTo?: string | null },
): { ok: true; asset: Asset } | { ok: false; errorKey: string } {
  const current = assetItems.find((x) => x.id === id || x.tag === id);
  if (!current) return { ok: false, errorKey: 'module.errors.notFound' };
  const allowed = ASSET_TRANSITIONS[current.status] ?? [];
  if (!allowed.includes(next)) {
    return { ok: false, errorKey: 'module.errors.invalidTransition' };
  }
  if (next === 'in_use' && !(opts?.assignedTo ?? current.assignedTo)) {
    return { ok: false, errorKey: 'assets.validation.assignRequired' };
  }
  assetItems = assetItems.map((a) => {
    if (a.id !== current.id) return a;
    return {
      ...a,
      status: next,
      assignedTo:
        next === 'stock' || next === 'retired'
          ? null
          : opts?.assignedTo !== undefined
            ? opts.assignedTo
            : a.assignedTo,
      updatedAt: nowIso(),
    };
  });
  pushModuleActivity(current.id, 'status', `module.activity.asset_${next}`);
  notifySecondary();
  return { ok: true, asset: getAsset(current.id)! };
}

// ── Problems ─────────────────────────────────────────────────────────

export function listProblems(): Problem[] {
  return problemItems.map(cloneProblem);
}

export function getProblem(id: string): Problem | null {
  const p = problemItems.find((x) => x.id === id || x.number === id);
  return p ? cloneProblem(p) : null;
}

let problemSeq = 100;

export function addProblem(payload: CreateProblemPayload): Problem {
  problemSeq += 1;
  const id = `pr-${problemSeq}`;
  const number = `PRB-${problemSeq}`;
  const next: Problem = {
    id,
    number,
    title: payload.title.trim(),
    description: payload.description?.trim() || undefined,
    status: 'new',
    priority: payload.priority ?? 'medium',
    knownError: Boolean(payload.knownError && payload.rootCause?.trim()),
    relatedIncidents: 0,
    assignee: null,
    updatedAt: nowIso(),
    createdAt: nowIso(),
    service: payload.service?.trim() || undefined,
    rootCause: payload.rootCause?.trim() || undefined,
    workaround: payload.workaround?.trim() || undefined,
  };
  problemItems = [next, ...problemItems];
  pushModuleActivity(id, 'system', 'module.activity.created');
  notifySecondary();
  return cloneProblem(next);
}

const PROBLEM_TRANSITIONS: Record<string, WorkItemStatus[]> = {
  new: ['in_progress', 'cancelled'],
  in_progress: ['waiting', 'resolved', 'cancelled'],
  waiting: ['in_progress', 'resolved', 'cancelled'],
  resolved: ['closed', 'in_progress'],
  closed: [],
  cancelled: [],
};

export function getProblemTransitions(status: WorkItemStatus): WorkItemStatus[] {
  return PROBLEM_TRANSITIONS[status] ?? [];
}

export function transitionProblem(
  id: string,
  next: WorkItemStatus,
  opts?: { rootCause?: string; workaround?: string; knownError?: boolean },
): { ok: true; problem: Problem } | { ok: false; errorKey: string } {
  const current = problemItems.find((x) => x.id === id || x.number === id);
  if (!current) return { ok: false, errorKey: 'module.errors.notFound' };
  const allowed = PROBLEM_TRANSITIONS[current.status] ?? [];
  if (!allowed.includes(next)) {
    return { ok: false, errorKey: 'module.errors.invalidTransition' };
  }
  if (next === 'resolved') {
    const rc = (opts?.rootCause ?? current.rootCause)?.trim();
    if (!rc) return { ok: false, errorKey: 'problems.validation.rootCauseRequired' };
  }
  if (opts?.knownError === true) {
    const rc = (opts?.rootCause ?? current.rootCause)?.trim();
    if (!rc) return { ok: false, errorKey: 'problems.validation.knownErrorNeedsCause' };
  }
  problemItems = problemItems.map((p) => {
    if (p.id !== current.id) return p;
    return {
      ...p,
      status: next,
      rootCause: opts?.rootCause?.trim() || p.rootCause,
      workaround: opts?.workaround?.trim() || p.workaround,
      knownError:
        opts?.knownError !== undefined ? opts.knownError : p.knownError,
      assignee:
        next === 'in_progress' && !p.assignee
          ? actorFromCurrent()
          : p.assignee,
      updatedAt: nowIso(),
    };
  });
  pushModuleActivity(current.id, 'status', `module.activity.problem_${next}`);
  notifySecondary();
  return { ok: true, problem: getProblem(current.id)! };
}

export function updateProblemFields(
  id: string,
  patch: Partial<
    Pick<
      Problem,
      | 'rootCause'
      | 'workaround'
      | 'knownError'
      | 'description'
      | 'priority'
      | 'assignee'
    >
  >,
): { ok: true; problem: Problem } | { ok: false; errorKey: string } {
  const current = problemItems.find((x) => x.id === id || x.number === id);
  if (!current) return { ok: false, errorKey: 'module.errors.notFound' };
  if (patch.knownError === true) {
    const rc = (patch.rootCause ?? current.rootCause)?.trim();
    if (!rc) return { ok: false, errorKey: 'problems.validation.knownErrorNeedsCause' };
  }
  problemItems = problemItems.map((p) => {
    if (p.id !== current.id) return p;
    return {
      ...p,
      ...patch,
      assignee:
        patch.assignee === undefined
          ? p.assignee
          : patch.assignee
            ? { ...patch.assignee }
            : null,
      updatedAt: nowIso(),
    };
  });
  if (patch.knownError !== undefined) {
    pushModuleActivity(
      current.id,
      'field',
      patch.knownError
        ? 'module.activity.known_error_set'
        : 'module.activity.known_error_cleared',
    );
  }
  if (patch.rootCause) {
    pushModuleActivity(current.id, 'field', 'module.activity.root_cause_set');
  }
  notifySecondary();
  return { ok: true, problem: getProblem(current.id)! };
}

// ── Changes ──────────────────────────────────────────────────────────

export function listChanges(): Change[] {
  return changeItems.map(cloneChange);
}

export function getChange(id: string): Change | null {
  const c = changeItems.find((x) => x.id === id || x.number === id);
  return c ? cloneChange(c) : null;
}

let changeSeq = 430;

export function addChange(payload: CreateChangePayload): Change {
  changeSeq += 1;
  const id = `ch-${changeSeq}`;
  const number = `CHG-${changeSeq}`;
  const start =
    payload.plannedStart ||
    new Date(Date.now() + 86400000).toISOString().slice(0, 16);
  const end =
    payload.plannedEnd ||
    new Date(Date.now() + 86400000 + 2 * 3600000).toISOString().slice(0, 16);
  const next: Change = {
    id,
    number,
    title: payload.title.trim(),
    description: payload.description?.trim() || undefined,
    type: payload.type ?? 'normal',
    status: 'draft',
    risk: payload.risk ?? 'medium',
    plannedStart: start.length === 16 ? `${start}:00.000Z` : start,
    plannedEnd: end.length === 16 ? `${end}:00.000Z` : end,
    assignee: actorFromCurrent(),
    updatedAt: nowIso(),
    createdAt: nowIso(),
    service: payload.service?.trim() || undefined,
    implementationPlan: payload.implementationPlan?.trim() || undefined,
    backoutPlan: payload.backoutPlan?.trim() || undefined,
    cabApproved: false,
  };
  changeItems = [next, ...changeItems];
  pushModuleActivity(id, 'system', 'module.activity.created');
  notifySecondary();
  return cloneChange(next);
}

const CHANGE_TRANSITIONS: Record<ChangeStatus, ChangeStatus[]> = {
  draft: ['cab_review', 'scheduled', 'cancelled'],
  cab_review: ['scheduled', 'draft', 'cancelled'],
  scheduled: ['in_progress', 'cancelled'],
  in_progress: ['completed', 'cancelled'],
  completed: [],
  cancelled: [],
};

export function getChangeTransitions(status: ChangeStatus): ChangeStatus[] {
  return CHANGE_TRANSITIONS[status] ?? [];
}

export function transitionChange(
  id: string,
  next: ChangeStatus,
): { ok: true; change: Change } | { ok: false; errorKey: string } {
  const current = changeItems.find((x) => x.id === id || x.number === id);
  if (!current) return { ok: false, errorKey: 'module.errors.notFound' };
  const allowed = CHANGE_TRANSITIONS[current.status] ?? [];
  if (!allowed.includes(next)) {
    return { ok: false, errorKey: 'module.errors.invalidTransition' };
  }

  // Standard may skip CAB: draft → scheduled. Normal/emergency must pass CAB.
  if (next === 'scheduled' && current.type !== 'standard' && current.status === 'draft') {
    return { ok: false, errorKey: 'changes.validation.cabRequired' };
  }
  if (next === 'scheduled') {
    if (!current.implementationPlan?.trim()) {
      return { ok: false, errorKey: 'changes.validation.planRequired' };
    }
    if (!current.backoutPlan?.trim()) {
      return { ok: false, errorKey: 'changes.validation.backoutRequired' };
    }
  }
  if (next === 'cab_review' && !current.implementationPlan?.trim()) {
    return { ok: false, errorKey: 'changes.validation.planRequired' };
  }

  changeItems = changeItems.map((c) => {
    if (c.id !== current.id) return c;
    return {
      ...c,
      status: next,
      cabApproved:
        next === 'scheduled' &&
        (current.status === 'cab_review' || current.type === 'standard')
          ? true
          : c.cabApproved,
      updatedAt: nowIso(),
    };
  });
  const textKey =
    next === 'cab_review'
      ? 'module.activity.submitted_cab'
      : next === 'scheduled'
        ? 'module.activity.scheduled'
        : next === 'completed'
          ? 'module.activity.completed'
          : next === 'cancelled'
            ? 'module.activity.cancelled'
            : `module.activity.change_${next}`;
  pushModuleActivity(current.id, 'status', textKey);
  notifySecondary();
  return { ok: true, change: getChange(current.id)! };
}

export function updateChangeFields(
  id: string,
  patch: Partial<
    Pick<
      Change,
      | 'implementationPlan'
      | 'backoutPlan'
      | 'description'
      | 'risk'
      | 'plannedStart'
      | 'plannedEnd'
    >
  >,
): { ok: true; change: Change } | { ok: false; errorKey: string } {
  const current = changeItems.find((x) => x.id === id || x.number === id);
  if (!current) return { ok: false, errorKey: 'module.errors.notFound' };
  changeItems = changeItems.map((c) => {
    if (c.id !== current.id) return c;
    return { ...c, ...patch, updatedAt: nowIso() };
  });
  pushModuleActivity(current.id, 'field', 'module.activity.fields_updated');
  notifySecondary();
  return { ok: true, change: getChange(current.id)! };
}

export type { ImpactLevel, UrgencyLevel };
