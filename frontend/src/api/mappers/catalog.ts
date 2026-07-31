import type { CatalogCategory, CatalogService } from '@/types';

export interface BackendCatalogItem {
  id: string;
  key?: string;
  status?: string;
  formDefinitionId?: string | null;
  workflowDefinitionId?: string | null;
  locale?: string;
  name: string;
  description?: string | null;
  category?: string | null;
}

const ICONS: CatalogService['icon'][] = [
  'key',
  'laptop',
  'monitor',
  'shield',
  'cloud',
  'server',
];

const CATEGORY_ICONS: CatalogCategory['icon'][] = [
  'key',
  'laptop',
  'monitor',
  'shield',
];

const CATEGORY_TONES: CatalogCategory['tone'][] = [
  'lilac',
  'blue',
  'mint',
  'coral',
];

function hash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}

export function mapCatalogService(dto: BackendCatalogItem): CatalogService {
  const id = String(dto.id);
  const cat = dto.category ?? 'general';
  return {
    id,
    titleKey: dto.name,
    descriptionKey: dto.description ?? dto.name,
    metaKey: dto.key ?? id,
    categoryId: cat,
    icon: ICONS[hash(id) % ICONS.length],
    popular: false,
    approvalRequired: false,
  };
}

/** Derive category cards from catalog items (backend has no categories resource). */
export function deriveCatalogCategories(
  items: CatalogService[],
): CatalogCategory[] {
  const counts = new Map<string, number>();
  for (const s of items) {
    counts.set(s.categoryId, (counts.get(s.categoryId) ?? 0) + 1);
  }
  return [...counts.entries()].map(([id, count], i) => ({
    id,
    titleKey: id,
    descriptionKey: id,
    count,
    icon: CATEGORY_ICONS[i % CATEGORY_ICONS.length],
    tone: CATEGORY_TONES[i % CATEGORY_TONES.length],
  }));
}
