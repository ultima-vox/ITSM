/**
 * Resolve technical related ids (ci-*, wi-*, as-*, pr-*, ch-*, kb-*) to human labels
 * and deep-link hrefs using the in-memory mock store.
 * Used by Assets / Problems / Changes drawers and global search Open.
 */
import {
  getAsset,
  getChange,
  getConfigurationItem,
  getKnowledgeArticle,
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

  const article = getKnowledgeArticle(id);
  if (article) {
    const title = (article.title ?? article.titleKey ?? id).trim();
    return title.length > 48 ? `${title.slice(0, 47)}…` : title;
  }

  // Fallback: never surface a bare technical id when we can soft-label it
  if (id.startsWith('ci-')) return id.replace(/^ci-/, 'CI · ');
  if (id.startsWith('wi-')) return id.replace(/^wi-/, 'WI · ');
  if (id.startsWith('as-')) return id.replace(/^as-/, 'Asset · ');
  if (id.startsWith('pr-')) return id.replace(/^pr-/, 'PR · ');
  if (id.startsWith('ch-')) return id.replace(/^ch-/, 'CHG · ');
  if (id.startsWith('kb-')) return id.replace(/^kb-/, 'KB · ');
  return id;
}

/** Deep-link path for an entity id when resolvable from store or id prefix. */
export function resolveRelatedHref(id: string): string | undefined {
  if (!id) return undefined;
  const enc = encodeURIComponent(id);

  if (getWorkItem(id)) return `/work-items/${id}`;
  if (getConfigurationItem(id)) return `/cmdb?ci=${enc}`;
  if (getProblem(id)) return `/problems?id=${enc}`;
  if (getChange(id)) return `/changes?id=${enc}`;
  if (getAsset(id)) return `/assets?id=${enc}`;
  if (getKnowledgeArticle(id)) return `/knowledge?article=${enc}`;

  // Prefix fallbacks (live payloads / unknown store)
  if (id.startsWith('wi-')) return `/work-items/${id}`;
  if (id.startsWith('ci-')) return `/cmdb?ci=${enc}`;
  if (id.startsWith('pr-')) return `/problems?id=${enc}`;
  if (id.startsWith('ch-')) return `/changes?id=${enc}`;
  if (id.startsWith('as-')) return `/assets?id=${enc}`;
  if (id.startsWith('kb-')) return `/knowledge?article=${enc}`;
  return undefined;
}

export type RelatedKind =
  | 'work_item'
  | 'ci'
  | 'asset'
  | 'problem'
  | 'change'
  | 'knowledge'
  | 'unknown';

export function resolveRelatedKind(id: string): RelatedKind {
  if (getWorkItem(id) || id.startsWith('wi-')) return 'work_item';
  if (getConfigurationItem(id) || id.startsWith('ci-')) return 'ci';
  if (getAsset(id) || id.startsWith('as-')) return 'asset';
  if (getProblem(id) || id.startsWith('pr-')) return 'problem';
  if (getChange(id) || id.startsWith('ch-')) return 'change';
  if (getKnowledgeArticle(id) || id.startsWith('kb-')) return 'knowledge';
  return 'unknown';
}
