/**
 * Platform audit trail — mock list for Admin → Audit.
 * Live backend endpoint not wired yet; always uses seed in mock mode.
 */

import { apiRequest, delay, useMock } from './client';
import { auditEvents as seedAudit } from '@/mock/data';
import type { AuditEvent } from '@/types';

export type AuditActionFilter = string | 'all';

export async function fetchAuditEvents(options?: {
  action?: AuditActionFilter;
  limit?: number;
  signal?: AbortSignal;
}): Promise<AuditEvent[]> {
  const limit = options?.limit ?? 100;
  const action = options?.action && options.action !== 'all' ? options.action : null;

  if (useMock()) {
    await delay(160);
    let list = seedAudit.map((e) => ({
      ...e,
      actor: { ...e.actor },
    }));
    if (action) {
      list = list.filter((e) => e.action === action);
    }
    return list.slice(0, limit);
  }

  const qs = new URLSearchParams();
  if (action) qs.set('action', action);
  qs.set('limit', String(limit));
  const hits = await apiRequest<AuditEvent[]>(`/audit?${qs}`, {
    signal: options?.signal,
  });
  return hits ?? [];
}

/** Distinct action keys present in mock (and used for filter chips). */
export function listAuditActionKeys(): string[] {
  const set = new Set(seedAudit.map((e) => e.action));
  return [...set].sort();
}
