/**
 * Platform full-text search — GET /api/v1/search?q=
 */

import { apiRequest, delay, useMock } from './client';
import { resolveRelatedHref } from '@/lib/resolveRelated';
import {
  listAssets,
  listChanges,
  listConfigurationItems,
  listKnowledgeArticles,
  listProblems,
  listWorkItems,
} from '@/mock/store';

export interface SearchHit {
  id: string;
  objectType: string;
  title: string;
  body?: string;
  scopes?: string[];
  updatedAt?: string;
  facets?: Record<string, unknown>;
}

/** Known object-type chips for the full search page (mock + live). */
export const SEARCH_OBJECT_TYPES = [
  'work-item',
  'knowledge',
  'ci',
  'asset',
  'problem',
  'change',
] as const;

export type SearchObjectType = (typeof SEARCH_OBJECT_TYPES)[number];

/**
 * Resolve a search hit to an in-app deep-link path.
 * Prefers `resolveRelatedHref` (store + id prefix); falls back to objectType.
 */
export function searchHitPath(hit: SearchHit): string | null {
  const id = hit.id;
  if (!id) return null;

  const fromRelated = resolveRelatedHref(id);
  if (fromRelated) return fromRelated;

  // Type-based deep links when store/prefix cannot resolve (live API ids)
  const type = (hit.objectType || '').toLowerCase();
  const enc = encodeURIComponent(id);

  if (
    type === 'work-item' ||
    type === 'workitem' ||
    type === 'incident' ||
    type === 'request'
  ) {
    return `/work-items/${id}`;
  }
  if (type === 'knowledge' || type === 'article' || type === 'kb') {
    return `/knowledge?article=${enc}`;
  }
  if (type === 'ci' || type === 'configuration-item' || type === 'cmdb') {
    return `/cmdb?ci=${enc}`;
  }
  if (type === 'asset') return `/assets?id=${enc}`;
  if (type === 'problem') return `/problems?id=${enc}`;
  if (type === 'change') return `/changes?id=${enc}`;
  if (!type || type.includes('work')) {
    return `/work-items/${id}`;
  }
  return null;
}

function mockSearchAll(needle: string, limit: number): SearchHit[] {
  const lower = needle.toLowerCase();
  const hits: SearchHit[] = [];

  for (const w of listWorkItems()) {
    const hay = `${w.number} ${w.title} ${w.description} ${w.service} ${w.type}`.toLowerCase();
    if (!hay.includes(lower)) continue;
    hits.push({
      id: w.id,
      objectType: 'work-item',
      title: `${w.number} · ${w.title}`,
      body: w.description,
      scopes: ['work-item', w.type],
      updatedAt: w.updatedAt,
      facets: {
        number: w.number,
        state: w.status,
        priority: w.priority,
        service: w.service,
      },
    });
  }

  for (const a of listKnowledgeArticles()) {
    const title = a.title ?? a.titleKey;
    const summary = a.summary ?? a.summaryKey;
    const hay = `${title} ${summary} ${a.body ?? ''} ${a.tagKey}`.toLowerCase();
    if (!hay.includes(lower)) continue;
    hits.push({
      id: a.id,
      objectType: 'knowledge',
      title: a.title ?? a.titleKey,
      body: a.summary ?? a.summaryKey,
      scopes: ['knowledge'],
      updatedAt: a.updatedAt,
    });
  }

  for (const ci of listConfigurationItems()) {
    const hay = `${ci.name} ${ci.kindKey} ${ci.owner} ${ci.environment ?? ''}`.toLowerCase();
    if (!hay.includes(lower)) continue;
    hits.push({
      id: ci.id,
      objectType: 'ci',
      title: ci.name,
      body: `${ci.kindKey} · ${ci.owner}`,
      scopes: ['ci', ci.kindKey],
      facets: { status: ci.status },
    });
  }

  for (const asset of listAssets()) {
    const hay =
      `${asset.tag} ${asset.name} ${asset.typeKey} ${asset.location} ${asset.serial ?? ''}`.toLowerCase();
    if (!hay.includes(lower)) continue;
    hits.push({
      id: asset.id,
      objectType: 'asset',
      title: `${asset.tag} · ${asset.name}`,
      body: `${asset.typeKey} · ${asset.location}`,
      scopes: ['asset'],
      updatedAt: asset.updatedAt,
      facets: { status: asset.status },
    });
  }

  for (const p of listProblems()) {
    const hay =
      `${p.number} ${p.title} ${p.description ?? ''} ${p.service ?? ''} ${p.rootCause ?? ''}`.toLowerCase();
    if (!hay.includes(lower)) continue;
    hits.push({
      id: p.id,
      objectType: 'problem',
      title: `${p.number} · ${p.title}`,
      body: p.description,
      scopes: ['problem'],
      updatedAt: p.updatedAt,
      facets: { status: p.status, priority: p.priority },
    });
  }

  for (const c of listChanges()) {
    const hay =
      `${c.number} ${c.title} ${c.description ?? ''} ${c.service ?? ''} ${c.type}`.toLowerCase();
    if (!hay.includes(lower)) continue;
    hits.push({
      id: c.id,
      objectType: 'change',
      title: `${c.number} · ${c.title}`,
      body: c.description,
      scopes: ['change', c.type],
      updatedAt: c.updatedAt,
      facets: { status: c.status, risk: c.risk },
    });
  }

  return hits.slice(0, limit);
}

/**
 * Live: GET /search?q=
 * Mock: filter local work items, KB, CMDB, assets, problems, changes.
 */
export async function searchAll(
  q: string,
  options?: {
    limit?: number;
    signal?: AbortSignal;
    objectTypes?: string[];
  },
): Promise<SearchHit[]> {
  const limit = options?.limit ?? 20;
  const needle = q.trim();
  if (!needle) return [];

  let hits: SearchHit[];

  if (useMock()) {
    await delay(180);
    hits = mockSearchAll(needle, Math.max(limit, 80));
  } else {
    const qs = new URLSearchParams({
      q: needle,
      limit: String(limit),
    });
    if (options?.objectTypes?.length) {
      qs.set('types', options.objectTypes.join(','));
    }
    hits =
      (await apiRequest<SearchHit[]>(`/search?${qs}`, {
        signal: options?.signal,
      })) ?? [];
  }

  if (options?.objectTypes?.length) {
    const allowed = new Set(options.objectTypes.map((t) => t.toLowerCase()));
    hits = hits.filter((h) => allowed.has((h.objectType || '').toLowerCase()));
  }

  return hits.slice(0, limit);
}
