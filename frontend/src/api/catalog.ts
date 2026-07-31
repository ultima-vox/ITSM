import { delay, useMock, apiRequest } from './client';
import {
  deriveCatalogCategories,
  mapCatalogService,
  type BackendCatalogItem,
} from './mappers/catalog';
import { catalogCategories, catalogServices } from '@/mock/data';
import type { CatalogCategory, CatalogService } from '@/types';

export async function fetchCatalogCategories(): Promise<CatalogCategory[]> {
  if (useMock()) {
    await delay(200);
    return catalogCategories;
  }
  // Backend has GET /catalog/items only — derive categories from items
  const items = await apiRequest<BackendCatalogItem[]>('/catalog/items');
  const services = (items ?? []).map(mapCatalogService);
  return deriveCatalogCategories(services);
}

export async function fetchCatalogServices(q?: string): Promise<CatalogService[]> {
  if (useMock()) {
    await delay(220);
    if (!q) return catalogServices;
    const needle = q.toLowerCase();
    return catalogServices.filter(
      (s) =>
        s.titleKey.toLowerCase().includes(needle) ||
        s.id.toLowerCase().includes(needle),
    );
  }
  const qs = new URLSearchParams();
  if (q) qs.set('q', q);
  const suffix = qs.toString() ? `?${qs}` : '';
  const items = await apiRequest<BackendCatalogItem[]>(`/catalog/items${suffix}`);
  return (items ?? []).map(mapCatalogService);
}
