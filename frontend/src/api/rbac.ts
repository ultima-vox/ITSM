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
} from '@/mock/rbac';
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
  throw new Error('module.errors.bulkLiveUnsupported');
}

export function getPermissionDescription(key: string): string {
  if (isMockMode()) return mockPermDesc(key);
  return key;
}

export function rbacWritable(): boolean {
  return isMockMode();
}

export function subscribeRbac(listener: () => void): () => void {
  if (isMockMode()) return mockSubscribe(listener);
  return () => undefined;
}
