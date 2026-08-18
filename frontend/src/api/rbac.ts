/**
 * RBAC catalog — live roles/permissions/principals; mock for user directory edits.
 */
import { apiRequest, delay, isMockMode } from './client';
import {
  assignUserRole as mockAssign,
  getPermissionDescription as mockPermDesc,
  listRbacRoles as mockRoles,
  listRbacUsers as mockUsers,
  subscribeRbac as mockSubscribe,
  getUserPermissions as mockUserPermissions,
} from '@/mock/rbac';
import { currentUser } from '@/mock/data';
import type { RbacPermission, RbacRole, RbacRoleKey, RbacUser } from '@/types';

interface BackendRole {
  id: string;
  roleKey: string;
  labels: Record<string, string>;
  description: string;
  permissions: string[];
}

interface BackendPermission {
  key: string;
  description: string;
}

interface BackendPrincipal {
  subjectId: string;
  roleKeys: string[];
}

export interface RoleDelegation {
  id: string;
  delegatorId: string;
  delegateeId: string;
  roleKey: string;
  startsAt: string;
  expiresAt: string;
  reason: string;
  createdBy: string;
  createdAt: string;
  revokedBy?: string;
  revokedAt?: string;
}

export interface CreateRoleDelegation {
  delegatorId: string;
  delegateeId: string;
  roleKey: string;
  startsAt?: string;
  expiresAt: string;
  reason: string;
}

export interface EffectiveAccess {
  subjectId: string;
  roles: string[];
  permissions: string[];
}

export async function fetchMyEffectiveAccess(): Promise<EffectiveAccess> {
  if (isMockMode()) {
    await delay(40);
    return { subjectId: currentUser.id, roles: [], permissions: mockUserPermissions(currentUser.id) };
  }
  return apiRequest<EffectiveAccess>('/rbac/me');
}

function mapRole(dto: BackendRole): RbacRole {
  const labels = dto.labels ?? {};
  return {
    id: dto.id,
    roleKey: dto.roleKey as RbacRoleKey,
    labels: {
      en: labels.en ?? dto.roleKey,
      ru: labels.ru ?? labels.en ?? dto.roleKey,
      de: labels.de,
    },
    description: dto.description ?? '',
    permissions: dto.permissions ?? [],
  };
}

function mapPrincipal(dto: BackendPrincipal): RbacUser {
  const primary = (dto.roleKeys?.[0] ?? 'REQUESTER') as RbacRoleKey;
  const subject = dto.subjectId;
  const short =
    subject.length > 12 ? subject.slice(0, 8) : subject;
  return {
    id: subject,
    subjectId: subject,
    name: subject,
    email: `${short}@local`,
    initials: short.slice(0, 2).toUpperCase(),
    roleKey: primary,
    locale: 'ru',
    status: 'active',
  };
}

export async function fetchRbacRoles(): Promise<RbacRole[]> {
  if (isMockMode()) {
    await delay(120);
    return mockRoles();
  }
  const list = await apiRequest<BackendRole[]>('/rbac/roles');
  return (list ?? []).map(mapRole);
}

export async function fetchRbacUsers(): Promise<RbacUser[]> {
  if (isMockMode()) {
    await delay(120);
    return mockUsers();
  }
  const list = await apiRequest<BackendPrincipal[]>('/rbac/principals');
  return (list ?? []).map(mapPrincipal);
}

export async function fetchRbacPermissions(): Promise<RbacPermission[]> {
  if (isMockMode()) {
    await delay(80);
    return [];
  }
  const list = await apiRequest<BackendPermission[]>('/rbac/permissions');
  return (list ?? []).map((p) => ({ key: p.key, description: p.description }));
}

export async function assignUserRole(
  userId: string,
  roleKey: RbacRoleKey,
): Promise<RbacUser | null> {
  if (isMockMode()) {
    await delay(80);
    return mockAssign(userId, roleKey);
  }
  const changed = await apiRequest<BackendPrincipal>(
    `/rbac/principals/${encodeURIComponent(userId)}/role`,
    { method: 'PUT', body: { roleKey } },
  );
  return mapPrincipal(changed);
}

export async function fetchRoleDelegations(): Promise<RoleDelegation[]> {
  if (isMockMode()) return [];
  return apiRequest<RoleDelegation[]>('/rbac/delegations');
}

export async function createRoleDelegation(
  input: CreateRoleDelegation,
): Promise<RoleDelegation> {
  return apiRequest<RoleDelegation>('/rbac/delegations', {
    method: 'POST',
    body: input,
  });
}

export async function revokeRoleDelegation(id: string): Promise<void> {
  await apiRequest<void>(`/rbac/delegations/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });
}

const permDescCache = new Map<string, string>();

export function getPermissionDescription(key: string): string {
  if (isMockMode()) return mockPermDesc(key);
  const cached = permDescCache.get(key);
  if (cached) return cached;
  // Lazy-fetch permission descriptions in background (fire-and-forget)
  if (permDescCache.size === 0) {
    fetchRbacPermissions().then((list) => {
      for (const p of list) permDescCache.set(p.key, p.description);
    }).catch(() => {/* ignore */});
  }
  return key;
}

export function rbacWritable(): boolean {
  return true;
}

export function subscribeRbac(listener: () => void): () => void {
  if (isMockMode()) return mockSubscribe(listener);
  return () => undefined;
}
