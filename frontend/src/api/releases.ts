import { apiRequest, delay, isMockMode } from './client';
import {
  addRelease as storeAddRelease,
  availableTransitions as storeAvailableTransitions,
  getRelease as storeGetRelease,
  linkReleaseChanges as storeLinkChanges,
  listReleaseContent as storeListContent,
  listReleases as storeListReleases,
  recordGoDecision as storeRecordGoDecision,
  transitionRelease as storeTransitionRelease,
  unlinkReleaseChange as storeUnlinkChange,
  updateRelease as storeUpdateRelease,
} from '@/mock/releases';
import type {
  CreateReleasePayload,
  Release,
  ReleaseContent,
  ReleaseContentEntry,
  ReleaseGoDecision,
  ReleaseStatus,
  ReleaseType,
} from '@/types';

interface ReleaseListResponse {
  items: Release[];
  total: number;
  page: number;
  size: number;
}

export interface ReleaseListResult {
  items: Release[];
  total: number;
}

export interface ListReleasesOptions {
  status?: ReleaseStatus | 'all';
  type?: ReleaseType | 'all';
  q?: string;
  page?: number;
  size?: number;
  signal?: AbortSignal;
}

function matchesFilters(release: Release, options?: ListReleasesOptions): boolean {
  if (options?.status && options.status !== 'all' && release.status !== options.status) return false;
  if (options?.type && options.type !== 'all' && release.type !== options.type) return false;
  const q = options?.q?.trim().toLowerCase();
  if (q) {
    return (
      release.number.toLowerCase().includes(q) || release.name.toLowerCase().includes(q)
    );
  }
  return true;
}

export async function fetchReleases(options?: ListReleasesOptions): Promise<ReleaseListResult> {
  const page = options?.page ?? 0;
  const size = options?.size ?? 50;

  if (isMockMode()) {
    await delay(140);
    const all = storeListReleases().filter((release) => matchesFilters(release, options));
    return { items: all.slice(page * size, page * size + size), total: all.length };
  }

  const qs = new URLSearchParams();
  if (options?.status && options.status !== 'all') qs.set('status', options.status);
  if (options?.type && options.type !== 'all') qs.set('type', options.type);
  if (options?.q?.trim()) qs.set('q', options.q.trim());
  qs.set('page', String(page));
  qs.set('size', String(size));
  const response = await apiRequest<ReleaseListResponse>(`/releases?${qs}`, {
    signal: options?.signal,
  });
  return { items: response?.items ?? [], total: response?.total ?? 0 };
}

export async function fetchRelease(id: string, signal?: AbortSignal): Promise<Release> {
  if (isMockMode()) {
    await delay(80);
    return storeGetRelease(id);
  }
  return apiRequest<Release>(`/releases/${id}`, { signal });
}

export async function createRelease(payload: CreateReleasePayload): Promise<Release> {
  if (isMockMode()) {
    await delay(160);
    return storeAddRelease(payload);
  }
  return apiRequest<Release>('/releases', { method: 'POST', body: payload });
}

export interface UpdateReleasePayload {
  expectedVersion: number;
  name?: string;
  type?: ReleaseType;
  description?: string;
  deploymentPlan?: string;
  rollbackPlan?: string;
  testSummary?: string;
  releaseManager?: string;
  plannedStart?: string;
  plannedEnd?: string;
}

export async function updateRelease(
  id: string,
  payload: UpdateReleasePayload,
): Promise<Release> {
  if (isMockMode()) {
    await delay(140);
    const patch: Partial<Release> = {};
    if (payload.name !== undefined) patch.name = payload.name;
    if (payload.type !== undefined) patch.type = payload.type;
    if (payload.description !== undefined) patch.description = payload.description;
    if (payload.deploymentPlan !== undefined) patch.deploymentPlan = payload.deploymentPlan;
    if (payload.rollbackPlan !== undefined) patch.rollbackPlan = payload.rollbackPlan;
    if (payload.testSummary !== undefined) patch.testSummary = payload.testSummary;
    if (payload.releaseManager !== undefined) patch.releaseManager = payload.releaseManager;
    if (payload.plannedStart !== undefined) patch.plannedStart = payload.plannedStart;
    if (payload.plannedEnd !== undefined) patch.plannedEnd = payload.plannedEnd;
    return storeUpdateRelease(id, patch);
  }
  return apiRequest<Release>(`/releases/${id}`, { method: 'PATCH', body: payload });
}

export async function transitionRelease(
  id: string,
  target: ReleaseStatus,
  expectedVersion?: number,
): Promise<Release> {
  if (isMockMode()) {
    await delay(140);
    return storeTransitionRelease(id, target);
  }
  return apiRequest<Release>(`/releases/${id}/transitions`, {
    method: 'POST',
    body: { target, expectedVersion },
  });
}

export async function recordGoDecision(
  id: string,
  decision: ReleaseGoDecision,
  notes?: string,
  expectedVersion?: number,
): Promise<Release> {
  if (isMockMode()) {
    await delay(140);
    return storeRecordGoDecision(id, decision, notes);
  }
  return apiRequest<Release>(`/releases/${id}/go-decision`, {
    method: 'POST',
    body: { decision, notes, expectedVersion },
  });
}

function toContent(items: ReleaseContentEntry[]): ReleaseContent {
  const blocking = items.filter((entry) => !entry.deployable).length;
  return { items, total: items.length, blocking, deployable: blocking === 0 };
}

export async function fetchReleaseContent(
  id: string,
  signal?: AbortSignal,
): Promise<ReleaseContent> {
  if (isMockMode()) {
    await delay(90);
    return toContent(storeListContent(id));
  }
  return apiRequest<ReleaseContent>(`/releases/${id}/changes`, { signal });
}

export async function linkReleaseChanges(
  id: string,
  changeIds: string[],
  mockChanges?: Array<{ id: string; number: string; title: string; type: string; status: string }>,
): Promise<ReleaseContent> {
  if (isMockMode()) {
    await delay(140);
    return toContent(storeLinkChanges(id, mockChanges ?? []));
  }
  return apiRequest<ReleaseContent>(`/releases/${id}/changes`, {
    method: 'POST',
    body: { changeIds },
  });
}

export async function unlinkReleaseChange(
  id: string,
  changeId: string,
): Promise<ReleaseContent> {
  if (isMockMode()) {
    await delay(120);
    return toContent(storeUnlinkChange(id, changeId));
  }
  return apiRequest<ReleaseContent>(`/releases/${id}/changes/${changeId}`, {
    method: 'DELETE',
  });
}

export async function fetchReleaseTransitions(
  id: string,
  status: ReleaseStatus,
  signal?: AbortSignal,
): Promise<ReleaseStatus[]> {
  if (isMockMode()) {
    await delay(60);
    return storeAvailableTransitions(status);
  }
  const targets = await apiRequest<string[]>(`/releases/${id}/transitions`, { signal });
  return (targets ?? []) as ReleaseStatus[];
}
