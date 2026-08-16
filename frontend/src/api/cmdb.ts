import { delay, useMock, apiRequest, getApiActorId } from './client';
import {
  mapAsset,
  mapCiRelationship,
  mapConfigurationItem,
  type BackendAsset,
  type BackendCi,
  type BackendCiRelationship,
} from './mappers/cmdb';
import { ciImpactScenario } from '@/mock/data';
import {
  addAsset as storeAddAsset,
  addCiRelation as storeAddCiRelation,
  addConfigurationItem,
  bulkAssignAssets as storeBulkAssignAssets,
  bulkSetAssetStatus as storeBulkSetAssetStatus,
  getAssetTransitions,
  listAssets,
  listCiRelations,
  listConfigurationItems,
  removeCiRelation as storeRemoveCiRelation,
  updateCiRelation as storeUpdateCiRelation,
  subscribeConfigurationItems,
  subscribeSecondaryModules,
  transitionAsset as storeTransitionAsset,
} from '@/mock/store';
import type {
  Asset,
  AssetStatus,
  CiImpactEntry,
  CiRelation,
  CiRelationType,
  CiStatus,
  ConfigurationItem,
  CreateAssetPayload,
} from '@/types';

export async function fetchConfigurationItems(): Promise<ConfigurationItem[]> {
  if (useMock()) {
    await delay(240);
    return listConfigurationItems();
  }
  const list = await apiRequest<BackendCi[]>('/cmdb/cis');
  return (list ?? []).map(mapConfigurationItem);
}

export async function fetchCiRelations(): Promise<CiRelation[]> {
  if (useMock()) {
    await delay(120);
    return listCiRelations();
  }
  try {
    const list = await apiRequest<BackendCiRelationship[]>('/cmdb/relations');
    return (list ?? []).map(mapCiRelationship);
  } catch {
    return [];
  }
}

/** CIs with zero relationships — live orphan detection. */
export async function fetchOrphanCis(): Promise<ConfigurationItem[]> {
  if (useMock()) {
    await delay(80);
    const cis = listConfigurationItems();
    const rels = listCiRelations();
    const linked = new Set<string>();
    for (const r of rels) {
      linked.add(r.fromId);
      linked.add(r.toId);
    }
    return cis.filter((c) => !linked.has(c.id));
  }
  const list = await apiRequest<BackendCi[]>('/cmdb/orphans?limit=200');
  return (list ?? []).map(mapConfigurationItem);
}

export async function createCiRelation(input: {
  fromId: string;
  toId: string;
  type: CiRelationType;
}): Promise<{ ok: true; relation: CiRelation } | { ok: false; errorKey: string }> {
  if (useMock()) {
    await delay(100);
    return storeAddCiRelation(input);
  }
  try {
    const dto = await apiRequest<BackendCiRelationship>('/cmdb/relations', {
      method: 'POST',
      body: {
        fromId: input.fromId,
        toId: input.toId,
        sourceCiId: input.fromId,
        targetCiId: input.toId,
        relationType: input.type,
        type: input.type?.toUpperCase?.() ?? input.type,
      },
    });
    return { ok: true, relation: mapCiRelationship(dto) };
  } catch {
    return { ok: false, errorKey: 'cmdb.relForm.error' };
  }
}

export async function deleteCiRelation(
  id: string,
): Promise<{ ok: true } | { ok: false; errorKey: string }> {
  if (useMock()) {
    await delay(80);
    return storeRemoveCiRelation(id);
  }
  try {
    await apiRequest(`/cmdb/relations/${id}`, { method: 'DELETE' });
    return { ok: true };
  } catch {
    return { ok: false, errorKey: 'cmdb.relForm.error' };
  }
}

export async function updateCiRelation(
  id: string,
  patch: { type: CiRelationType },
): Promise<{ ok: true; relation: CiRelation } | { ok: false; errorKey: string }> {
  if (useMock()) {
    await delay(80);
    return storeUpdateCiRelation(id, patch);
  }
  try {
    const relation = await apiRequest<CiRelation>(`/cmdb/relations/${id}`, {
      method: 'PATCH',
      body: patch,
    });
    return { ok: true, relation };
  } catch {
    return { ok: false, errorKey: 'cmdb.relForm.error' };
  }
}

export async function fetchCiImpact(options?: {
  rootCiId?: string;
  hops?: number;
}): Promise<{
  changeKey: string;
  rootCiId: string;
  entries: CiImpactEntry[];
}> {
  if (useMock()) {
    await delay(160);
    return {
      changeKey: ciImpactScenario.changeKey,
      rootCiId: options?.rootCiId || ciImpactScenario.rootCiId,
      entries: ciImpactScenario.entries.map((e) => ({ ...e })),
    };
  }
  const root = options?.rootCiId?.trim();
  if (!root) {
    return { changeKey: 'cmdb.impact.changePg', rootCiId: '', entries: [] };
  }
  try {
    const hops = options?.hops ?? 3;
    const result = await apiRequest<{
      rootCiId?: string;
      rootName?: string;
      hops?: number;
      impacted?: Array<{
        id: string;
        name?: string | null;
        hop: number;
        viaRelationship?: string | null;
      }>;
    }>(`/cmdb/cis/${encodeURIComponent(root)}/impact?hops=${hops}`);
    const entries: CiImpactEntry[] = (result.impacted ?? []).map((n) => ({
      ciId: String(n.id),
      hop: (n.hop <= 1 ? 1 : 2) as 1 | 2,
      impact: n.hop <= 1 ? 'high' : 'medium',
    }));
    return {
      changeKey: result.rootName
        ? `Impact: ${result.rootName}`
        : 'cmdb.impact.changePg',
      rootCiId: String(result.rootCiId ?? root),
      entries,
    };
  } catch {
    return { changeKey: 'cmdb.impact.changePg', rootCiId: root, entries: [] };
  }
}

export async function createConfigurationItem(input: {
  name: string;
  kindKey: string;
  status: CiStatus;
  owner?: string;
}): Promise<ConfigurationItem> {
  if (useMock()) {
    await delay(180);
    return addConfigurationItem(input);
  }
  const created = await apiRequest<BackendCi>('/cmdb/cis', {
    method: 'POST',
    body: {
      name: input.name,
      classKey: input.kindKey,
      kindKey: input.kindKey,
      status: (input.status ?? 'operational').toUpperCase(),
      owner: input.owner,
    },
  });
  return mapConfigurationItem(created);
}

export { subscribeConfigurationItems };
export { subscribeSecondaryModules };

export async function fetchAssets(): Promise<Asset[]> {
  if (useMock()) {
    await delay(240);
    return listAssets();
  }
  const list = await apiRequest<BackendAsset[]>('/assets');
  return (list ?? []).map(mapAsset);
}

export async function createAsset(payload: CreateAssetPayload): Promise<Asset> {
  if (useMock()) {
    await delay(180);
    return storeAddAsset(payload);
  }
  const kind = mapFrontendAssetKind(payload.typeKey);
  const created = await apiRequest<BackendAsset>('/assets', {
    method: 'POST',
    body: {
      assetTag: payload.tag ?? payload.name,
      tag: payload.tag ?? payload.name,
      kind,
      status: 'IN_STOCK',
      ownerSubject: payload.assignedTo ?? null,
      acquiredOn: payload.purchasedAt ?? null,
    },
  });
  return mapAsset(created);
}

function mapFrontendAssetKind(typeKey?: string): string {
  const k = (typeKey ?? '').toLowerCase();
  if (k.includes('laptop')) return 'LAPTOP';
  if (k.includes('monitor')) return 'MONITOR';
  if (k.includes('phone') || k.includes('mobile')) return 'MOBILE_DEVICE';
  if (k.includes('server')) return 'SERVER';
  return 'OTHER';
}

export async function transitionAssetStatus(
  id: string,
  next: AssetStatus,
  opts?: { assignedTo?: string | null },
): Promise<{ ok: true; asset: Asset } | { ok: false; errorKey: string }> {
  if (useMock()) {
    await delay(120);
    return storeTransitionAsset(id, next, opts);
  }
  try {
    const dto = await apiRequest<BackendAsset>(`/assets/${id}/transition`, {
      method: 'POST',
      body: { status: next, ...opts },
    });
    return { ok: true, asset: mapAsset(dto) };
  } catch {
    return { ok: false, errorKey: 'module.errors.invalidTransition' };
  }
}

export { getAssetTransitions };

export async function bulkAssignAssets(ids: string[]): Promise<number> {
  if (useMock()) {
    await delay(80);
    return storeBulkAssignAssets(ids);
  }
  const actor = getApiActorId();
  const settled = await Promise.all(
    ids.map(async (id) => {
      try {
        await apiRequest(`/assets/${id}/assign`, {
          method: 'POST',
          body: { ownerSubject: actor },
        });
        return true;
      } catch {
        return false;
      }
    }),
  );
  return settled.filter(Boolean).length;
}

export async function bulkSetAssetStatus(
  ids: string[],
  status: AssetStatus,
): Promise<number> {
  if (useMock()) {
    await delay(80);
    return storeBulkSetAssetStatus(ids, status);
  }
  const backendStatus = mapAssetStatusToBackend(status);
  const settled = await Promise.all(
    ids.map(async (id) => {
      try {
        await apiRequest(`/assets/${id}/transition`, {
          method: 'POST',
          body: { status: backendStatus },
        });
        return true;
      } catch {
        return false;
      }
    }),
  );
  return settled.filter(Boolean).length;
}

function mapAssetStatusToBackend(status: AssetStatus): string {
  switch (status) {
    case 'in_use':
      return 'IN_USE';
    case 'stock':
      return 'IN_STOCK';
    case 'repair':
      return 'REPAIRED';
    case 'retired':
      return 'RETIRED';
    default:
      return 'IN_STOCK';
  }
}
