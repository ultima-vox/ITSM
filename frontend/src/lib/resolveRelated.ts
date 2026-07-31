/**
 * Resolve technical related ids (ci-*, wi-*, as-*, pr-*, ch-*) to human labels
 * using the in-memory mock store. Used by Assets / Problems / Changes drawers.
 */
import {
  getAsset,
  getChange,
  getConfigurationItem,
  getProblem,
  getWorkItem,
} from '@/mock/store';

export function resolveRelatedLabel(id: string): string {
  if (!id) return id;

  const wi = getWorkItem(id);
  if (wi) {
    const title =
      wi.title.length > 48 ? `${wi.title.slice(0, 47)}…` : wi.title;
    return `${wi.number} · ${title}`;
  }

  const ci = getConfigurationItem(id);
  if (ci) return ci.name;

  const asset = getAsset(id);
  if (asset) return `${asset.tag} · ${asset.name}`;

  const problem = getProblem(id);
  if (problem) {
    const title =
      problem.title.length > 48
        ? `${problem.title.slice(0, 47)}…`
        : problem.title;
    return `${problem.number} · ${title}`;
  }

  const change = getChange(id);
  if (change) {
    const title =
      change.title.length > 48
        ? `${change.title.slice(0, 47)}…`
        : change.title;
    return `${change.number} · ${title}`;
  }

  // Fallback: never surface a bare technical id when we can soft-label it
  if (id.startsWith('ci-')) return id.replace(/^ci-/, 'CI · ');
  if (id.startsWith('wi-')) return id.replace(/^wi-/, 'WI · ');
  return id;
}

export function resolveRelatedHref(id: string): string | undefined {
  if (!id) return undefined;
  if (getWorkItem(id)) return `/work-items/${id}`;
  if (getConfigurationItem(id)) return `/cmdb?ci=${encodeURIComponent(id)}`;
  if (getProblem(id)) return '/problems';
  if (getChange(id)) return '/changes';
  if (getAsset(id)) return '/assets';
  if (id.startsWith('wi-')) return `/work-items/${id}`;
  if (id.startsWith('ci-')) return `/cmdb?ci=${encodeURIComponent(id)}`;
  return undefined;
}

export type RelatedKind = 'work_item' | 'ci' | 'asset' | 'problem' | 'change' | 'unknown';

export function resolveRelatedKind(id: string): RelatedKind {
  if (getWorkItem(id) || id.startsWith('wi-')) return 'work_item';
  if (getConfigurationItem(id) || id.startsWith('ci-')) return 'ci';
  if (getAsset(id) || id.startsWith('as-')) return 'asset';
  if (getProblem(id) || id.startsWith('pr-')) return 'problem';
  if (getChange(id) || id.startsWith('ch-')) return 'change';
  return 'unknown';
}
