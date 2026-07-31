import { delay, useMock, apiRequest } from './client';
import {
  mapAsset,
  mapConfigurationItem,
  type BackendAsset,
  type BackendCi,
} from './mappers/cmdb';
import { configurationItems, assets } from '@/mock/data';
import type { Asset, ConfigurationItem } from '@/types';

export async function fetchConfigurationItems(): Promise<ConfigurationItem[]> {
  if (useMock()) {
    await delay(240);
    return configurationItems;
  }
  const list = await apiRequest<BackendCi[]>('/cmdb/cis');
  return (list ?? []).map(mapConfigurationItem);
}

export async function fetchAssets(): Promise<Asset[]> {
  if (useMock()) {
    await delay(240);
    return assets;
  }
  const list = await apiRequest<BackendAsset[]>('/assets');
  return (list ?? []).map(mapAsset);
}
