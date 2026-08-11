/**
 * Platform audit trail — Admin → Audit.
 * Live: GET /api/v1/audit (+ /actions for filter chips).
 */

import { apiRequest, delay, isMockMode } from './client';
import { auditEvents as seedAudit } from '@/mock/data';
import type { AuditEvent, Person } from '@/types';

export type AuditActionFilter = string | 'all';

interface BackendAuditEvent {
  id: string;
  occurredAt?: string;
  at?: string;
  actorId?: string;
  actor?: { id?: string; name?: string; initials?: string } | null;
  action: string;
  objectType: string;
  objectId?: string | null;
  objectLabel?: string | null;
  detail?: string | null;
  correlationId?: string | null;
}

function initialsFrom(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }
  return name.slice(0, 2).toUpperCase() || '?';
}

function mapActor(dto: BackendAuditEvent): Person {
  if (dto.actor?.id || dto.actor?.name) {
    const id = dto.actor.id ?? dto.actorId ?? 'unknown';
    const name = dto.actor.name ?? id;
    return {
      id,
      name,
      initials: dto.actor.initials ?? initialsFrom(name),
    };
  }
  const id = dto.actorId ?? 'unknown';
  return { id, name: id, initials: initialsFrom(id) };
}

function mapLive(dto: BackendAuditEvent): AuditEvent {
  return {
    id: String(dto.id),
    at: dto.at ?? dto.occurredAt ?? new Date().toISOString(),
    actor: mapActor(dto),
    action: dto.action,
    objectType: dto.objectType,
    objectId: dto.objectId ?? undefined,
    objectLabel: dto.objectLabel ?? undefined,
    detail: dto.detail ?? undefined,
  };
}

export async function fetchAuditEvents(options?: {
  action?: AuditActionFilter;
  limit?: number;
  signal?: AbortSignal;
}): Promise<AuditEvent[]> {
  const limit = options?.limit ?? 100;
  const action = options?.action && options.action !== 'all' ? options.action : null;

  if (isMockMode()) {
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
  const hits = await apiRequest<BackendAuditEvent[]>(`/audit?${qs}`, {
    signal: options?.signal,
  });
  return (hits ?? []).map(mapLive);
}

/** Distinct action keys for filter chips. Live hits backend; mock uses seed. */
export async function fetchAuditActionKeys(
  signal?: AbortSignal,
): Promise<string[]> {
  if (isMockMode()) {
    await delay(40);
    return listAuditActionKeys();
  }
  const keys = await apiRequest<string[]>('/audit/actions', { signal });
  return keys ?? [];
}

/** Sync helper for mock seed (AuditPage may still use until live keys load). */
export function listAuditActionKeys(): string[] {
  const set = new Set(seedAudit.map((e) => e.action));
  return [...set].sort();
}
