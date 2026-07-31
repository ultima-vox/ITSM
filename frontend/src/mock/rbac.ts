/**
 * Mock RBAC catalog + session-scoped user role assignments.
 * Seeded from backend Flyway V10/V12/V13/V15 + Keycloak demo principals (V14).
 */
import type {
  LocaleCode,
  RbacPermission,
  RbacRole,
  RbacRoleKey,
  RbacUser,
  RbacUserStatus,
} from '@/types';

type Listener = () => void;

const listeners = new Set<Listener>();

function notify() {
  listeners.forEach((fn) => fn());
}

export function subscribeRbac(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Full permission catalog (V10 + V12 + V13 + V15). */
export const RBAC_PERMISSIONS: RbacPermission[] = [
  { key: 'work-item.read', description: 'Read work items' },
  { key: 'work-item.create', description: 'Create work items' },
  { key: 'work-item.update', description: 'Update work items' },
  { key: 'work-item.transition', description: 'Transition work item workflow' },
  { key: 'work-item.assign', description: 'Assign work items' },
  { key: 'work-item.close', description: 'Close work items' },
  { key: 'work-item.comment', description: 'Add comments on work items' },
  { key: 'change.read', description: 'Read changes' },
  { key: 'change.write', description: 'Create and transition changes' },
  { key: 'change.approve', description: 'Approve changes' },
  { key: 'change.manage', description: 'Manage changes' },
  { key: 'problem.read', description: 'Read problems' },
  { key: 'problem.write', description: 'Create and update problems' },
  { key: 'catalog.read', description: 'List and view service catalog items' },
  { key: 'catalog.request', description: 'Submit catalog service requests' },
  { key: 'knowledge.read', description: 'Read published knowledge articles' },
  { key: 'knowledge.vote', description: 'Vote on knowledge article helpfulness' },
  { key: 'cmdb.read', description: 'Read configuration items and impact' },
  { key: 'asset.read', description: 'Read assets' },
  { key: 'asset.write', description: 'Mutate assets' },
  { key: 'attachment.read', description: 'Read attachment metadata and download content' },
  { key: 'attachment.write', description: 'Upload attachments' },
  { key: 'metadata.read', description: 'Read object/form/workflow metadata' },
  { key: 'metadata.write', description: 'Write object/form/workflow/translation metadata' },
  { key: 'search.read', description: 'Full-text search API' },
  { key: 'ai.summarize', description: 'Use AI copilot summarize' },
  { key: 'ai.suggest', description: 'Use AI copilot resolution suggestions' },
  { key: 'admin.translations', description: 'Administer UI translation catalog' },
  { key: 'admin.full', description: 'Full administrative access' },
];

const ALL_PERM_KEYS = RBAC_PERMISSIONS.map((p) => p.key);

function role(
  roleKey: RbacRoleKey,
  labels: RbacRole['labels'],
  description: string,
  permissions: string[],
): RbacRole {
  return {
    id: `role-${roleKey}`,
    roleKey,
    labels,
    description,
    permissions: [...permissions].sort(),
  };
}

/**
 * Role → permission matrix from V10 seed + later grant migrations.
 * ADMIN effectively holds every permission (admin.full short-circuits checks).
 */
const SEED_ROLES: RbacRole[] = [
  role(
    'ADMIN',
    {
      en: 'Administrator',
      ru: 'Администратор',
      de: 'Administrator',
    },
    'Full platform administration',
    ALL_PERM_KEYS,
  ),
  role(
    'SERVICE_DESK_AGENT',
    {
      en: 'Service Desk Agent',
      ru: 'Агент службы поддержки',
      de: 'Service-Desk-Agent',
    },
    'Handles incidents and requests',
    [
      'work-item.read',
      'work-item.create',
      'work-item.update',
      'work-item.transition',
      'work-item.assign',
      'work-item.comment',
      'metadata.read',
      'attachment.read',
      'attachment.write',
      'search.read',
    ],
  ),
  role(
    'SERVICE_DESK_MANAGER',
    {
      en: 'Service Desk Manager',
      ru: 'Менеджер службы поддержки',
      de: 'Service-Desk-Manager',
    },
    'Manages service desk operations',
    [
      'work-item.read',
      'work-item.create',
      'work-item.update',
      'work-item.transition',
      'work-item.assign',
      'work-item.close',
      'work-item.comment',
      'metadata.read',
      'attachment.read',
      'attachment.write',
      'search.read',
    ],
  ),
  role(
    'REQUESTER',
    {
      en: 'Requester',
      ru: 'Заявитель',
      de: 'Anforderer',
    },
    'End-user who submits requests',
    [
      'work-item.read',
      'work-item.create',
      'catalog.read',
      'catalog.request',
      'knowledge.read',
      'knowledge.vote',
      'search.read',
    ],
  ),
  role(
    'CHANGE_MANAGER',
    {
      en: 'Change Manager',
      ru: 'Менеджер изменений',
      de: 'Change-Manager',
    },
    'Owns change process',
    [
      'change.read',
      'change.write',
      'change.approve',
      'change.manage',
      'work-item.read',
      'metadata.read',
      'problem.read',
      'problem.write',
      'cmdb.read',
      'search.read',
    ],
  ),
  role(
    'CAB_MEMBER',
    {
      en: 'CAB Member',
      ru: 'Участник CAB',
      de: 'CAB-Mitglied',
    },
    'Change advisory board member',
    ['change.read', 'change.approve', 'work-item.read'],
  ),
];

function user(
  id: string,
  name: string,
  email: string,
  initials: string,
  roleKey: RbacRoleKey,
  locale: LocaleCode,
  status: RbacUserStatus,
  subjectId?: string,
): RbacUser {
  return { id, name, email, initials, roleKey, locale, status, subjectId };
}

/** Demo directory — Keycloak principals + mock operators from people catalog. */
const SEED_USERS: RbacUser[] = [
  user(
    'u-anna',
    'Anna Yakovleva',
    'anna@itsm.local',
    'АЯ',
    'SERVICE_DESK_AGENT',
    'ru',
    'active',
    'a0000000-0000-4000-8000-000000000001',
  ),
  user(
    'u-admin',
    'Platform Admin',
    'admin@itsm.local',
    'PA',
    'ADMIN',
    'en',
    'active',
    'a0000000-0000-4000-8000-000000000002',
  ),
  user(
    'u-requester',
    'Self-Service Requester',
    'requester@itsm.local',
    'SR',
    'REQUESTER',
    'en',
    'active',
    'a0000000-0000-4000-8000-000000000003',
  ),
  user(
    'u-alexey',
    'Алексей К.',
    'alexey.k@northstar.example',
    'АК',
    'SERVICE_DESK_AGENT',
    'ru',
    'active',
  ),
  user(
    'u-maria',
    'Мария В.',
    'maria.v@northstar.example',
    'МВ',
    'SERVICE_DESK_MANAGER',
    'ru',
    'active',
  ),
  user(
    'u-dmitry',
    'Дмитрий С.',
    'dmitry.s@northstar.example',
    'ДС',
    'CHANGE_MANAGER',
    'en',
    'active',
  ),
  user(
    'u-olga',
    'Ольга П.',
    'olga.p@northstar.example',
    'ОП',
    'REQUESTER',
    'de',
    'active',
  ),
  user(
    'u-cab',
    'CAB Reviewer',
    'cab@northstar.example',
    'CR',
    'CAB_MEMBER',
    'en',
    'active',
  ),
  user(
    'u-locked',
    'Inactive Contractor',
    'contractor@northstar.example',
    'IC',
    'REQUESTER',
    'en',
    'inactive',
  ),
];

let roles: RbacRole[] = SEED_ROLES.map(cloneRole);
let users: RbacUser[] = SEED_USERS.map(cloneUser);

function cloneRole(r: RbacRole): RbacRole {
  return {
    ...r,
    labels: { ...r.labels },
    permissions: [...r.permissions],
  };
}

function cloneUser(u: RbacUser): RbacUser {
  return { ...u };
}

export function listRbacPermissions(): RbacPermission[] {
  return RBAC_PERMISSIONS.map((p) => ({ ...p }));
}

export function getPermissionDescription(key: string): string {
  return RBAC_PERMISSIONS.find((p) => p.key === key)?.description ?? key;
}

export function listRbacRoles(): RbacRole[] {
  return roles.map(cloneRole);
}

export function getRbacRole(roleKey: string): RbacRole | null {
  const found = roles.find((r) => r.roleKey === roleKey || r.id === roleKey);
  return found ? cloneRole(found) : null;
}

export function listRbacUsers(): RbacUser[] {
  return users.map(cloneUser);
}

export function getRbacUser(id: string): RbacUser | null {
  const found = users.find((u) => u.id === id);
  return found ? cloneUser(found) : null;
}

/** Session-store: assign a platform role to a user. */
export function assignUserRole(userId: string, roleKey: RbacRoleKey): RbacUser | null {
  const idx = users.findIndex((u) => u.id === userId);
  if (idx < 0) return null;
  if (!roles.some((r) => r.roleKey === roleKey)) return null;

  users[idx] = {
    ...users[idx],
    roleKey,
  };
  notify();
  return cloneUser(users[idx]);
}

export function setUserStatus(userId: string, status: RbacUserStatus): RbacUser | null {
  const idx = users.findIndex((u) => u.id === userId);
  if (idx < 0) return null;
  users[idx] = { ...users[idx], status };
  notify();
  return cloneUser(users[idx]);
}

export function resetRbac(): void {
  roles = SEED_ROLES.map(cloneRole);
  users = SEED_USERS.map(cloneUser);
  notify();
}
