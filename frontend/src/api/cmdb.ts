import { delay, useMock, apiRequest } from './client';
import {
  mapAsset,
  mapConfigurationItem,
  type BackendAsset,
  type BackendCi,
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
  // Backend may not expose relations yet — empty graph is honest
  try {
    const list = await apiRequest<CiRelation[]>('/cmdb/relations');
    return list ?? [];
  } catch {
    return [];
  }
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
    const relation = await apiRequest<CiRelation>('/cmdb/relations', {
      method: 'POST',
      body: input,
    });
    return { ok: true, relation };
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

export async function fetchCiImpact(): Promise<{
  changeKey: string;
  rootCiId: string;
  entries: CiImpactEntry[];
}> {
  if (useMock()) {
    await delay(160);
    return {
      changeKey: ciImpactScenario.changeKey,
      rootCiId: ciImpactScenario.rootCiId,
      entries: ciImpactScenario.entries.map((e) => ({ ...e })),
    };
  }
  try {
    return await apiRequest('/cmdb/impact');
  } catch {
    return { changeKey: 'cmdb.impact.changePg', rootCiId: '', entries: [] };
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
    body: input,
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
  const created = await apiRequest<BackendAsset>('/assets', {
    method: 'POST',
    body: payload,
  });
  return mapAsset(created);
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
  return ids.length;
}

export async function bulkSetAssetStatus(
  ids: string[],
  status: AssetStatus,
): Promise<number> {
  if (useMock()) {
    await delay(80);
    return storeBulkSetAssetStatus(ids, status);
  }
  return ids.length;
}
