import { ApiError, delay, isMockMode, apiRequest, getApiActorId } from './client';
import { preloadUserProfiles, resolveUsers } from './users';
import {
  mapActivity,
  mapComment,
  mapFrontendLevel,
  mapFrontendState,
  mapFrontendType,
  mapQueueStats,
  mapStats,
  mapWorkItem,
  type BackendActivity,
  type BackendComment,
  type BackendCreated,
  type BackendStats,
  type BackendWorkItem,
  type BackendWorkItemPage,
} from './mappers/workItem';
import { metrics, currentUser, TEAMS } from '@/mock/data';
import {
  addComment as storeAddComment,
  addWatcher as storeAddWatcher,
  addWorkItem,
  assignWorkItems as storeAssign,
  getActivities,
  getComments,
  getWorkItem,
  listWorkItems,
  removeWatcher as storeRemoveWatcher,
  resolveWorkItem as storeResolve,
  escalateWorkItem as storeEscalate,
  setWorkItemPriority as storeSetPriority,
  updateWorkItem as storeUpdate,
  type WorkItemPatch,
} from '@/mock/store';
import type {
  CreateWorkItemPayload,
  DashboardMetrics,
  Person,
  Priority,
  WorkItem,
  WorkItemActivity,
  WorkItemComment,
} from '@/types';

export const WORK_ITEM_LIST_PAGE_SIZE = 50;

export interface WorkItemListParams {
  assigneeId?: string;
  queue?: string;
  q?: string;
  state?: string;
  type?: string;
  priority?: string;
  unassigned?: boolean;
  teamId?: string;
  escalated?: boolean;
  service?: string;
  breached?: boolean;
  page?: number;
  size?: number;
}

export interface WorkItemListPage {
  items: WorkItem[];
  total: number;
  page: number;
  size: number;
}

export interface WorkItemQueueStats {
  open: number;
  mine: number;
  unassigned: number;
  breached: number;
}

export function buildWorkItemListSearchParams(params?: WorkItemListParams): URLSearchParams {
  const qs = new URLSearchParams();
  if (params?.assigneeId) qs.set('assigneeId', params.assigneeId);
  if (params?.q) qs.set('q', params.q);
  const state = toBackendWorkItemFilter('state', params?.state);
  const type = toBackendWorkItemFilter('type', params?.type);
  const priority = toBackendWorkItemFilter('priority', params?.priority);
  if (state) qs.set('state', state);
  if (type) qs.set('type', type);
  if (priority) qs.set('priority', priority);
  if (params?.unassigned) qs.set('unassigned', 'true');
  if (params?.teamId) qs.set('teamId', params.teamId);
  if (params?.escalated) qs.set('escalated', 'true');
  if (params?.service) qs.set('service', params.service);
  if (params?.breached) qs.set('breached', 'true');
  qs.set('page', String(params?.page ?? 0));
  qs.set('size', String(params?.size ?? WORK_ITEM_LIST_PAGE_SIZE));
  return qs;
}

function toBackendWorkItemFilter(
  kind: 'type' | 'state' | 'priority',
  value?: string,
): string | undefined {
  if (!value) return undefined;
  if (kind === 'type') {
    const normalized = value.toLowerCase();
    if (normalized === 'incident') return 'INCIDENT';
    if (normalized === 'request' || normalized === 'service_request') return 'SERVICE_REQUEST';
    return undefined;
  }
  if (kind === 'state') {
    if (value.toLowerCase() === 'waiting') return 'PENDING';
    return value.toUpperCase();
  }
  return value.toUpperCase();
}

export interface MajorIncident {
  id: string;
  workItemId: string;
  status: 'DECLARED' | 'RESOLVED';
  commanderId: string;
  summary: string;
  declaredAt: string;
  resolvedAt?: string | null;
}

const mockMajorIncidents = new Map<string, MajorIncident>();

export interface WorkItemTemplate {
  id: string;
  name: string;
  type: 'INCIDENT' | 'SERVICE_REQUEST';
  title: string;
  description: string;
  service: string;
  impact: 'LOW' | 'MEDIUM' | 'HIGH';
  urgency: 'LOW' | 'MEDIUM' | 'HIGH';
  teamId?: string | null;
  active: boolean;
  version: number;
}

const mockTemplates: WorkItemTemplate[] = [
  { id: 'mock-template-vpn', name: 'VPN access issue', type: 'INCIDENT',
    title: 'VPN connection unavailable', description: 'Describe device, location, error and last successful connection.',
    service: 'workplace', impact: 'MEDIUM', urgency: 'MEDIUM', active: true, version: 0 },
];

export async function fetchWorkItemTemplates(
  includeInactive = false,
): Promise<WorkItemTemplate[]> {
  if (isMockMode()) {
    await delay(40);
    return includeInactive
      ? mockTemplates.map((item) => ({ ...item }))
      : mockTemplates.filter((item) => item.active).map((item) => ({ ...item }));
  }
  const qs = includeInactive ? '?includeInactive=true' : '';
  return apiRequest<WorkItemTemplate[]>(`/work-item-templates${qs}`);
}

export type WorkItemTemplateDraft = Omit<
  WorkItemTemplate,
  'id' | 'active' | 'version'
>;

export async function createWorkItemTemplate(
  draft: WorkItemTemplateDraft,
): Promise<WorkItemTemplate> {
  if (isMockMode()) {
    await delay(80);
    const created: WorkItemTemplate = {
      ...draft,
      id: `mock-template-${Date.now()}`,
      active: true,
      version: 0,
    };
    mockTemplates.push(created);
    return { ...created };
  }
  return apiRequest<WorkItemTemplate>('/work-item-templates', {
    method: 'POST',
    body: draft,
  });
}

export async function updateWorkItemTemplate(
  id: string,
  version: number,
  draft: WorkItemTemplateDraft,
): Promise<void> {
  if (isMockMode()) {
    await delay(80);
    const idx = mockTemplates.findIndex((item) => item.id === id);
    if (idx < 0) throw new ApiError(404, 'not found');
    mockTemplates[idx] = {
      ...mockTemplates[idx],
      ...draft,
      version: mockTemplates[idx].version + 1,
    };
    return;
  }
  await apiRequest(`/work-item-templates/${encodeURIComponent(id)}?version=${version}`, {
    method: 'PUT',
    body: draft,
  });
}

export async function archiveWorkItemTemplate(
  id: string,
  version: number,
): Promise<void> {
  if (isMockMode()) {
    await delay(60);
    const idx = mockTemplates.findIndex((item) => item.id === id);
    if (idx < 0) throw new ApiError(404, 'not found');
    mockTemplates[idx] = {
      ...mockTemplates[idx],
      active: false,
      version: mockTemplates[idx].version + 1,
    };
    return;
  }
  await apiRequest(
    `/work-item-templates/${encodeURIComponent(id)}?version=${version}`,
    { method: 'DELETE' },
  );
}

export async function fetchMajorIncident(id: string): Promise<MajorIncident | null> {
  if (isMockMode()) {
    await delay(40);
    return mockMajorIncidents.get(id) ?? null;
  }
  try {
    return await apiRequest<MajorIncident>(`/work-items/${id}/major-incident`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

export async function declareMajorIncident(
  id: string,
  commanderId: string,
  summary: string,
): Promise<MajorIncident> {
  if (isMockMode()) {
    await delay(100);
    const incident: MajorIncident = {
      id: crypto.randomUUID(), workItemId: id, status: 'DECLARED', commanderId,
      summary, declaredAt: new Date().toISOString(), resolvedAt: null,
    };
    mockMajorIncidents.set(id, incident);
    return incident;
  }
  return apiRequest<MajorIncident>(`/work-items/${id}/major-incident`, {
    method: 'POST', body: { commanderId, summary },
  });
}

export async function resolveMajorIncident(id: string): Promise<MajorIncident> {
  if (isMockMode()) {
    await delay(100);
    const current = mockMajorIncidents.get(id);
    if (!current) throw new Error('Major incident not found');
    const resolved = { ...current, status: 'RESOLVED' as const, resolvedAt: new Date().toISOString() };
    mockMajorIncidents.set(id, resolved);
    return resolved;
  }
  return apiRequest<MajorIncident>(`/work-items/${id}/major-incident/resolve`, {
    method: 'POST',
  });
}

export async function fetchWorkItemPage(
  params?: WorkItemListParams,
): Promise<WorkItemListPage> {
  if (isMockMode()) {
    await delay();
    const items = listWorkItems(params);
    return {
      items,
      total: items.length,
      page: params?.page ?? 0,
      size: params?.size ?? items.length,
    };
  }
  const qs = buildWorkItemListSearchParams(params);
  const page = await apiRequest<BackendWorkItemPage>(`/work-items?${qs}`);
  const items = (page.items ?? []).map(mapWorkItem);
  const sids = items.flatMap((w) => [w.assignee?.id, w.requester?.id].filter(Boolean) as string[]);
  if (sids.length > 0) await preloadUserProfiles(sids);
  return {
    items,
    total: page.total ?? items.length,
    page: page.page ?? (params?.page ?? 0),
    size: page.size ?? (params?.size ?? WORK_ITEM_LIST_PAGE_SIZE),
  };
}

export async function fetchWorkItems(params?: WorkItemListParams): Promise<WorkItem[]> {
  const page = await fetchWorkItemPage(params);
  return page.items;
}

function isOpenQueueItem(w: WorkItem): boolean {
  return w.status !== 'closed' && w.status !== 'cancelled';
}

export async function fetchWorkItemQueueStats(): Promise<WorkItemQueueStats> {
  if (isMockMode()) {
    await delay();
    const list = listWorkItems();
    const actor = currentUser.id;
    const open = list.filter(isOpenQueueItem);
    return {
      open: open.length,
      mine: open.filter((w) => w.assignee?.id === actor).length,
      unassigned: open.filter((w) => !w.assignee).length,
      breached: open.filter((w) => w.slaState === 'breached').length,
    };
  }
  const stats = await apiRequest<BackendStats>('/work-items/stats');
  return mapQueueStats(stats);
}

export async function fetchMyOpenCount(): Promise<number> {
  if (isMockMode()) {
    const { countMyOpenAssigned } = await import('@/mock/store');
    return countMyOpenAssigned();
  }
  const actor = getApiActorId();
  if (!actor) return 0;
  const qs = new URLSearchParams({
    assigneeId: actor,
    page: '0',
    size: '1',
  });
  const page = await apiRequest<BackendWorkItemPage>(`/work-items?${qs}`);
  return page.total ?? 0;
}

export interface TransitionOption {
  key: string;
  target: string;
}

export async function fetchWorkItemTransitions(id: string): Promise<TransitionOption[]> {
  if (isMockMode()) {
    return [];
  }
  return apiRequest<TransitionOption[]>(`/work-items/${encodeURIComponent(id)}/transitions`);
}

export type WorkItemLinkType =
  | 'RELATED'
  | 'DUPLICATE_OF'
  | 'CAUSED_BY'
  | 'CHILD_OF';

export interface WorkItemLinkDto {
  id: string;
  sourceId: string;
  targetId: string;
  linkType: WorkItemLinkType | string;
  createdBy?: string;
  createdAt?: string;
}

function otherLinkedId(link: WorkItemLinkDto, selfId: string): string {
  return link.sourceId === selfId ? link.targetId : link.sourceId;
}

export async function fetchWorkItemLinks(
  id: string,
): Promise<WorkItemLinkDto[]> {
  if (isMockMode()) {
    await delay(40);
    const wi = getWorkItem(id);
    return (wi?.relatedIds ?? []).map((rid, i) => ({
      id: `mock-link-${id}-${i}`,
      sourceId: id,
      targetId: rid,
      linkType: 'RELATED',
    }));
  }
  const list = await apiRequest<WorkItemLinkDto[]>(`/work-items/${id}/links`);
  return list ?? [];
}

export async function createWorkItemLink(
  id: string,
  targetId: string,
  linkType: WorkItemLinkType = 'RELATED',
): Promise<WorkItemLinkDto> {
  if (isMockMode()) {
    await delay(80);
    const wi = getWorkItem(id);
    if (!wi) throw new Error('not found');
    const related = [...(wi.relatedIds ?? [])];
    if (!related.includes(targetId)) related.push(targetId);
    storeUpdate(id, { relatedIds: related });
    return {
      id: `mock-link-${id}-${targetId}`,
      sourceId: id,
      targetId,
      linkType,
    };
  }
  return apiRequest<WorkItemLinkDto>(`/work-items/${id}/links`, {
    method: 'POST',
    body: { targetId, linkType },
  });
}

export async function fetchWorkItemCiIds(id: string): Promise<string[]> {
  if (isMockMode()) {
    await delay(40);
    return getWorkItem(id)?.ciIds ?? [];
  }
  const list = await apiRequest<string[]>(
    `/work-items/${id}/configuration-items`,
  );
  return (list ?? []).map(String);
}

export async function linkWorkItemCi(
  id: string,
  configurationItemId: string,
): Promise<string[]> {
  if (isMockMode()) {
    await delay(80);
    const wi = getWorkItem(id);
    if (!wi) return [];
    const next = [...new Set([...(wi.ciIds ?? []), configurationItemId])];
    storeUpdate(id, { ciIds: next });
    return next;
  }
  const list = await apiRequest<string[]>(
    `/work-items/${id}/configuration-items`,
    {
      method: 'POST',
      body: { configurationItemId },
    },
  );
  return (list ?? []).map(String);
}

export async function unlinkWorkItemCi(
  id: string,
  configurationItemId: string,
): Promise<string[]> {
  if (isMockMode()) {
    await delay(60);
    const wi = getWorkItem(id);
    if (!wi) return [];
    const next = (wi.ciIds ?? []).filter((c) => c !== configurationItemId);
    storeUpdate(id, { ciIds: next });
    return next;
  }
  const list = await apiRequest<string[]>(
    `/work-items/${id}/configuration-items/${encodeURIComponent(configurationItemId)}`,
    { method: 'DELETE' },
  );
  return (list ?? []).map(String);
}

export async function deleteWorkItemLink(
  id: string,
  linkId: string,
): Promise<void> {
  if (isMockMode()) {
    await delay(60);
    const wi = getWorkItem(id);
    if (!wi) return;
    // mock link ids are mock-link-{id}-{index|target}
    const target = linkId.replace(`mock-link-${id}-`, '');
    const related = (wi.relatedIds ?? []).filter(
      (rid, i) => rid !== target && String(i) !== target,
    );
    storeUpdate(id, { relatedIds: related });
    return;
  }
  await apiRequest<void>(`/work-items/${id}/links/${encodeURIComponent(linkId)}`, {
    method: 'DELETE',
  });
}

export async function fetchWorkItem(id: string): Promise<WorkItem | null> {
  if (isMockMode()) {
    await delay();
    return getWorkItem(id);
  }
  try {
    const dto = await apiRequest<BackendWorkItem>(`/work-items/${id}`);
    // Pre-warm cache for this item's people
    await preloadUserProfiles([dto.assigneeId, dto.requesterId].filter(Boolean) as string[]);
    let item = mapWorkItem(dto);
    try {
      const subjects = await apiRequest<string[]>(`/work-items/${id}/watchers`);
      const watcherProfiles = subjects?.length ? await resolveUsers(subjects) : [];
      item = {
        ...item,
        watchers: watcherProfiles,
      };
    } catch {
      /* watchers optional */
    }
    try {
      const links = await fetchWorkItemLinks(id);
      const relatedIds = [
        ...new Set(links.map((l) => otherLinkedId(l, id)).filter(Boolean)),
      ];
      item = { ...item, relatedIds };
    } catch {
      /* links optional */
    }
    try {
      const ciIds = await apiRequest<string[]>(
        `/work-items/${id}/configuration-items`,
      );
      item = { ...item, ciIds: (ciIds ?? []).map(String) };
    } catch {
      /* CI links optional */
    }
    return item;
  } catch (err) {
    if (err && typeof err === 'object' && 'status' in err && (err as { status: number }).status === 404) {
      return null;
    }
    throw err;
  }
}

export async function fetchWorkItemActivity(
  id: string,
): Promise<WorkItemActivity[]> {
  if (isMockMode()) {
    await delay(180);
    return getActivities(id);
  }
  const list = await apiRequest<BackendActivity[]>(`/work-items/${id}/activity`);
  const actorIds = (list ?? []).map((a) => a.actorId).filter(Boolean) as string[];
  if (actorIds.length > 0) await preloadUserProfiles(actorIds);
  return (list ?? []).map(mapActivity);
}

export async function fetchWorkItemComments(
  id: string,
): Promise<WorkItemComment[]> {
  if (isMockMode()) {
    await delay(180);
    return getComments(id);
  }
  const list = await apiRequest<BackendComment[]>(`/work-items/${id}/comments`);
  const authorIds = (list ?? []).map((c) => c.authorId).filter(Boolean) as string[];
  if (authorIds.length > 0) await preloadUserProfiles(authorIds);
  return (list ?? []).map(mapComment);
}

export async function fetchDashboardMetrics(): Promise<DashboardMetrics> {
  if (isMockMode()) {
    await delay(200);
    const list = listWorkItems();
    const open = list.filter(
      (w) =>
        w.status === 'new' ||
        w.status === 'in_progress' ||
        w.status === 'waiting',
    ).length;
    const breached = list.filter((w) => w.slaState === 'breached').length;
    const dueToday = list.filter(
      (w) =>
        w.slaState === 'at_risk' ||
        w.slaState === 'breached' ||
        w.slaTarget.includes(':'),
    ).length;
    return {
      ...metrics,
      open,
      breached,
      dueToday,
      dueUrgent: list.filter((w) => w.slaState === 'at_risk').length,
      openDelta: metrics.openDelta,
      flow: {
        new: list.filter((w) => w.status === 'new').length,
        inProgress: list.filter((w) => w.status === 'in_progress').length,
        waiting: list.filter((w) => w.status === 'waiting').length,
      },
    };
  }
  const stats = await apiRequest<BackendStats>('/work-items/stats');
  const mapped = mapStats(stats);
  // Enrich flow from a small open list when possible
  try {
    const openItems = await fetchWorkItems();
    return {
      ...mapped,
      flow: {
        new: openItems.filter((w) => w.status === 'new').length,
        inProgress: openItems.filter((w) => w.status === 'in_progress').length,
        waiting: openItems.filter((w) => w.status === 'waiting').length,
      },
    };
  } catch {
    return mapped;
  }
}

export async function createWorkItem(
  payload: CreateWorkItemPayload,
): Promise<WorkItem> {
  if (isMockMode()) {
    await delay(400);
    const number =
      payload.kind === 'incident'
        ? `INC-${1800 + Math.floor(Math.random() * 200)}`
        : `REQ-${9000 + Math.floor(Math.random() * 200)}`;
    const item: WorkItem = {
      id: `wi-${Date.now()}`,
      number,
      title: payload.title,
      description: payload.description,
      type: payload.kind,
      priority: payload.priority ?? 'medium',
      status: 'new',
      assignee: null,
      requester: {
        id: currentUser.id,
        name: currentUser.name,
        initials: currentUser.initials,
        role: currentUser.role,
        teamId: currentUser.teamId,
      },
      service: payload.service,
      slaTarget: '08:00',
      slaState: 'on_track',
      updatedAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      queue: payload.queue ?? 'Service Desk L1',
      teamId: payload.teamId ?? TEAMS.sd,
      impact: payload.impact ?? 'medium',
      urgency:
        payload.urgency ?? (payload.kind === 'incident' ? 'high' : 'medium'),
      watchers: [],
      childTasks: [],
    };
    return addWorkItem(item);
  }

  const body = {
    type: mapFrontendType(payload.kind),
    title: payload.title,
    description: payload.description,
    service: payload.service,
    impact: (payload.impact ?? (payload.kind === 'incident' ? 'high' : 'medium')).toUpperCase(),
    urgency: (
      payload.urgency ?? (payload.kind === 'incident' ? 'high' : 'medium')
    ).toUpperCase(),
    teamId: payload.teamId,
  };
  const created = await apiRequest<BackendCreated>('/work-items', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  });
  // Create returns partial; fetch full WorkItemResponse
  const full = await apiRequest<BackendWorkItem>(`/work-items/${created.id}`);
  return mapWorkItem(full);
}

export async function bulkAssignWorkItems(
  ids: string[],
  assignee?: Person,
): Promise<void> {
  if (isMockMode()) {
    await delay(120);
    storeAssign(ids, assignee);
    return;
  }
  const assigneeId = assignee?.id ?? getApiActorId();
  await apiRequest<{ updated: number }>('/work-items/bulk/assign', {
    method: 'POST',
    body: { ids, assigneeId, teamId: assignee?.teamId },
  });
}

export async function bulkSetPriority(
  ids: string[],
  priority: Priority,
): Promise<void> {
  if (isMockMode()) {
    await delay(120);
    storeSetPriority(ids, priority);
    return;
  }
  await apiRequest<{ updated: number }>('/work-items/bulk/priority', {
    method: 'POST',
    body: { ids, priority: priority.toUpperCase() },
  });
}

export interface BulkWorkItemTransitionResult {
  id: string;
  success: boolean;
  status?: string | null;
  errorCode?: string | null;
}

export interface BulkWorkItemTransitionResponse {
  succeeded: number;
  results: BulkWorkItemTransitionResult[];
}

export async function bulkTransitionWorkItems(
  ids: string[],
  targetState: string,
  fields?: { resolutionCode?: string; resolutionNotes?: string },
): Promise<BulkWorkItemTransitionResponse> {
  if (isMockMode()) {
    await delay(120);
    const status = targetState.toLowerCase() === 'pending'
      ? 'waiting'
      : (targetState.toLowerCase().replace(/-/g, '_') as WorkItem['status']);
    const results: BulkWorkItemTransitionResult[] = ids.map((id) => {
      const item = storeUpdate(id, {
        status,
        resolutionNotes: fields?.resolutionNotes,
      });
      if (!item) {
        return { id, success: false, errorCode: 'NOT_FOUND' };
      }
      return { id, success: true, status: targetState };
    });
    return {
      succeeded: results.filter((row) => row.success).length,
      results,
    };
  }
  return apiRequest<BulkWorkItemTransitionResponse>('/work-items/bulk/transitions', {
    method: 'POST',
    body: {
      ids,
      targetState,
      resolutionCode: fields?.resolutionCode,
      resolutionNotes: fields?.resolutionNotes,
    },
  });
}

export async function assignWorkItemToMe(id: string): Promise<WorkItem | null> {
  if (isMockMode()) {
    await delay(120);
    storeAssign([id]);
    return getWorkItem(id);
  }
  const dto = await apiRequest<BackendWorkItem>(`/work-items/${id}/assign`, {
    method: 'POST',
    body: { assigneeId: getApiActorId() },
  });
  return mapWorkItem(dto);
}

export async function escalateWorkItem(id: string): Promise<WorkItem | null> {
  if (isMockMode()) {
    await delay(120);
    return storeEscalate(id);
  }
  const dto = await apiRequest<BackendWorkItem>(`/work-items/${id}/escalate`, {
    method: 'POST',
  });
  return mapWorkItem(dto);
}

export async function resolveWorkItem(
  id: string,
  resolutionNotes?: string,
): Promise<WorkItem | null> {
  if (isMockMode()) {
    await delay(120);
    return storeResolve(id, resolutionNotes);
  }
  const dto = await apiRequest<BackendWorkItem>(`/work-items/${id}/transitions`, {
    method: 'POST',
    body: {
      targetState: 'RESOLVED',
      resolutionCode: 'RESOLVED',
      resolutionNotes: resolutionNotes ?? '',
    },
  });
  return mapWorkItem(dto);
}

export async function patchWorkItem(
  id: string,
  patch: WorkItemPatch,
): Promise<WorkItem | null> {
  if (isMockMode()) {
    await delay(100);
    return storeUpdate(id, patch);
  }

  // Status changes go through transitions
  if (patch.status) {
    const body: Record<string, unknown> = {
      targetState: mapFrontendState(patch.status),
    };
    if (patch.status === 'resolved' || patch.status === 'closed') {
      body.resolutionCode = 'RESOLVED';
      body.resolutionNotes = patch.resolutionNotes ?? '';
    }
    const dto = await apiRequest<BackendWorkItem>(`/work-items/${id}/transitions`, {
      method: 'POST',
      body,
    });
    return mapWorkItem(dto);
  }

  // Assignment
  if (patch.assignee !== undefined) {
    const dto = await apiRequest<BackendWorkItem>(`/work-items/${id}/assign`, {
      method: 'POST',
      body: {
        assigneeId: patch.assignee?.id ?? null,
        teamId: patch.teamId ?? patch.assignee?.teamId,
      },
    });
    return mapWorkItem(dto);
  }

  const body: Record<string, unknown> = {};
  if (patch.title !== undefined) body.title = patch.title;
  if (patch.description !== undefined) body.description = patch.description;
  if (patch.service !== undefined) body.service = patch.service;
  if (patch.impact !== undefined) body.impact = mapFrontendLevel(patch.impact);
  if (patch.urgency !== undefined) body.urgency = mapFrontendLevel(patch.urgency);
  // Priority is derived from impact/urgency on backend
  if (patch.priority !== undefined && patch.impact === undefined && patch.urgency === undefined) {
    const p = patch.priority;
    body.impact =
      p === 'critical' || p === 'high' ? 'HIGH' : p === 'low' ? 'LOW' : 'MEDIUM';
    body.urgency =
      p === 'critical' ? 'HIGH' : p === 'high' ? 'HIGH' : p === 'low' ? 'LOW' : 'MEDIUM';
  }

  if (Object.keys(body).length === 0) {
    return fetchWorkItem(id);
  }

  const dto = await apiRequest<BackendWorkItem>(`/work-items/${id}`, {
    method: 'PATCH',
    body,
  });
  return mapWorkItem(dto);
}

export async function addWorkItemComment(
  id: string,
  body: string,
  opts?: { internal?: boolean },
): Promise<WorkItemComment | null> {
  if (isMockMode()) {
    await delay(100);
    return storeAddComment(id, body, opts);
  }
  const dto = await apiRequest<BackendComment>(`/work-items/${id}/comments`, {
    method: 'POST',
    body: { body, internal: Boolean(opts?.internal) },
  });
  return mapComment(dto);
}

export async function watchWorkItem(id: string): Promise<WorkItem | null> {
  if (isMockMode()) {
    await delay(80);
    return storeAddWatcher(id);
  }
  const subjects = await apiRequest<string[]>(`/work-items/${id}/watchers/me`, {
    method: 'POST',
  });
  const item = await fetchWorkItem(id);
  if (!item) return null;
  return {
    ...item,
    watchers: (subjects ?? []).map((sid) => ({
      id: sid,
      name: sid,
      initials: sid.slice(0, 2).toUpperCase(),
    })),
  };
}

export async function unwatchWorkItem(id: string): Promise<WorkItem | null> {
  if (isMockMode()) {
    await delay(80);
    return storeRemoveWatcher(id, currentUser.id);
  }
  const subjects = await apiRequest<string[]>(`/work-items/${id}/watchers/me`, {
    method: 'DELETE',
  });
  const item = await fetchWorkItem(id);
  if (!item) return null;
  return {
    ...item,
    watchers: (subjects ?? []).map((sid) => ({
      id: sid,
      name: sid,
      initials: sid.slice(0, 2).toUpperCase(),
    })),
  };
}

/** Transition helper for live mode (targetState uses backend enum names). */
export async function transitionWorkItem(
  id: string,
  targetState: string,
  fields?: { resolutionCode?: string; resolutionNotes?: string },
): Promise<WorkItem | null> {
  if (isMockMode()) {
    await delay(120);
    const status = targetState.toLowerCase() === 'pending'
      ? 'waiting'
      : (targetState.toLowerCase().replace(/-/g, '_') as WorkItem['status']);
    return storeUpdate(id, {
      status: status as WorkItem['status'],
      resolutionNotes: fields?.resolutionNotes,
    });
  }
  const dto = await apiRequest<BackendWorkItem>(`/work-items/${id}/transitions`, {
    method: 'POST',
    body: {
      targetState,
      resolutionCode: fields?.resolutionCode,
      resolutionNotes: fields?.resolutionNotes,
    },
  });
  return mapWorkItem(dto);
}

export interface WorkItemSurveyResult {
  workItemId: string;
  rating: number;
  comment?: string | null;
  submittedAt: string;
}

export async function submitWorkItemSurvey(
  id: string,
  rating: number,
  comment?: string,
): Promise<WorkItemSurveyResult> {
  if (isMockMode()) {
    await delay(120);
    return { workItemId: id, rating, comment: comment?.trim() || null, submittedAt: new Date().toISOString() };
  }
  return apiRequest<WorkItemSurveyResult>(`/work-items/${id}/survey`, {
    method: 'POST', body: { rating, comment: comment?.trim() || null },
  });
}

export interface DuplicateWorkItemMatch {
  id: string; number: string; title: string; state: string; priority: string;
  score: number; reason: string;
}

export async function findDuplicateWorkItems(
  title: string, description = '', signal?: AbortSignal,
): Promise<DuplicateWorkItemMatch[]> {
  if (isMockMode()) return [];
  const params = new URLSearchParams({ title, description, limit: '5' });
  return apiRequest<DuplicateWorkItemMatch[]>(`/work-items/duplicates?${params}`, { signal });
}
