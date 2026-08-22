/**
 * Identity source / IdP sync — Admin → Identity.
 * Live: GET /api/v1/identity/accounts and /group-mappings.
 * Mock: empty lists (no fake AD data).
 */
import { apiRequest, isMockMode } from './client';

export interface IdentityAccount {
  id: string;
  idp: string;
  externalId: string;
  subjectId: string;
  enabled: boolean;
  lastSync: string | null;
  roleKeys: string[];
}

export interface GroupRoleMapping {
  idpGroup: string;
  roleName: string;
}

interface BackendAccount {
  id?: string;
  idp: string;
  externalId: string;
  subjectId: string;
  enabled: boolean;
  lastSync?: string | null;
  roleKeys?: string[];
}

interface BackendMapping {
  idpGroup: string;
  roleName: string;
}

function mapAccount(dto: BackendAccount): IdentityAccount {
  return {
    id: dto.id ?? `${dto.idp}:${dto.externalId}`,
    idp: dto.idp,
    externalId: dto.externalId,
    subjectId: dto.subjectId,
    enabled: dto.enabled,
    lastSync: dto.lastSync ?? null,
    roleKeys: dto.roleKeys ?? [],
  };
}

export async function fetchIdentityAccounts(): Promise<IdentityAccount[]> {
  if (isMockMode()) return [];
  const list = await apiRequest<BackendAccount[]>('/identity/accounts');
  return (list ?? []).map(mapAccount);
}

export async function fetchIdentityGroupMappings(): Promise<GroupRoleMapping[]> {
  if (isMockMode()) return [];
  const list = await apiRequest<BackendMapping[]>('/identity/group-mappings');
  return (list ?? []).map((row) => ({
    idpGroup: row.idpGroup,
    roleName: row.roleName,
  }));
}
