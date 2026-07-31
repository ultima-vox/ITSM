/**
 * Platform full-text search — GET /api/v1/search?q=
 */

import { apiRequest, delay, useMock } from './client';
import { listWorkItems } from '@/mock/store';

export interface SearchHit {
  id: string;
  objectType: string;
  title: string;
  body?: string;
  scopes?: string[];
  updatedAt?: string;
  facets?: Record<string, unknown>;
}

/**
 * Live: GET /search?q=
 * Mock: filter local work items into SearchHit shape (for palette parity).
 */
export async function searchAll(
  q: string,
  options?: { limit?: number; signal?: AbortSignal },
): Promise<SearchHit[]> {
  const limit = options?.limit ?? 20;
  const needle = q.trim();
  if (!needle) return [];

  if (useMock()) {
    await delay(180);
    const lower = needle.toLowerCase();
    return listWorkItems()
      .filter((w) => {
        const hay = `${w.number} ${w.title} ${w.description} ${w.service} ${w.type}`.toLowerCase();
        return hay.includes(lower);
      })
      .slice(0, limit)
      .map((w) => ({
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
      }));
  }

  const qs = new URLSearchParams({
    q: needle,
    limit: String(limit),
  });
  const hits = await apiRequest<SearchHit[]>(`/search?${qs}`, {
    signal: options?.signal,
  });
  return hits ?? [];
}
