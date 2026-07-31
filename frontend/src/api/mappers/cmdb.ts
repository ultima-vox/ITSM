import type { Asset, ConfigurationItem, Priority } from '@/types';

export interface BackendCi {
  id: string;
  name: string;
  classKey?: string;
  status?: string;
  attributes?: Record<string, unknown> | null;
}

export interface BackendAsset {
  id: string;
  assetTag?: string;
  kind?: string;
  status?: string;
  ownerSubject?: string | null;
  configurationItemId?: string | null;
  acquiredOn?: string | null;
  warrantyUntil?: string | null;
}

const CI_ICONS: ConfigurationItem['icon'][] = [
  'server',
  'cloud',
  'network',
  'database',
  'app',
];

const CI_TONES: ConfigurationItem['tone'][] = [
  'violet',
  'cyan',
  'amber',
  'mint',
];

function hash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}

function mapCiStatus(status?: string): ConfigurationItem['status'] {
  switch ((status ?? 'OPERATIONAL').toUpperCase()) {
    case 'DEGRADED':
      return 'degraded';
    case 'MAINTENANCE':
      return 'maintenance';
    case 'RETIRED':
      return 'retired';
    default:
      return 'operational';
  }
}

function mapAssetStatus(status?: string): Asset['status'] {
  switch ((status ?? 'IN_STOCK').toUpperCase()) {
    case 'IN_USE':
      return 'in_use';
    case 'REPAIRED':
      return 'repair';
    case 'RETIRED':
    case 'LOST':
      return 'retired';
    case 'ORDERED':
    case 'IN_STOCK':
    default:
      return 'stock';
  }
}

function attrString(
  attrs: Record<string, unknown> | null | undefined,
  key: string,
): string | undefined {
  const v = attrs?.[key];
  return v == null ? undefined : String(v);
}

export function mapConfigurationItem(dto: BackendCi): ConfigurationItem {
  const id = String(dto.id);
  const attrs = dto.attributes ?? {};
  const criticalityRaw = attrString(attrs, 'criticality')?.toLowerCase();
  const criticality: Priority | undefined =
    criticalityRaw === 'critical' ||
    criticalityRaw === 'high' ||
    criticalityRaw === 'medium' ||
    criticalityRaw === 'low'
      ? criticalityRaw
      : undefined;

  return {
    id,
    name: dto.name,
    kindKey: dto.classKey ?? 'ci',
    status: mapCiStatus(dto.status),
    owner: attrString(attrs, 'owner') ?? attrString(attrs, 'ownerSubject') ?? '—',
    icon: CI_ICONS[hash(id) % CI_ICONS.length],
    tone: CI_TONES[hash(id) % CI_TONES.length],
    environment: attrString(attrs, 'environment'),
    criticality,
  };
}

export function mapAsset(dto: BackendAsset): Asset {
  return {
    id: String(dto.id),
    tag: dto.assetTag ?? String(dto.id),
    name: dto.assetTag ?? String(dto.id),
    typeKey: dto.kind ?? 'OTHER',
    status: mapAssetStatus(dto.status),
    assignedTo: dto.ownerSubject ?? null,
    location: '—',
    purchasedAt: dto.acquiredOn ?? new Date().toISOString().slice(0, 10),
    serial: undefined,
    model: dto.kind,
    notes: dto.configurationItemId
      ? `CI: ${dto.configurationItemId}`
      : undefined,
  };
}
