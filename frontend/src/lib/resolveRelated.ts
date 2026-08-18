/**
 * Resolve technical related ids (ci-*, wi-*, as-*, pr-*, ch-*, kb-*) to human labels
 * and deep-link hrefs.
 *
 * In mock mode, resolves from the in-memory store.
 * In live mode, uses prefix-based labeling (the entity is not in the mock store).
 */

function truncate(title: string): string {
  return title.length > 48 ? `${title.slice(0, 47)}…` : title;
}

// Lazy-loaded mock store (only in mock mode)
let mockStore: {
  getWorkItem: (id: string) => { number: string; title: string } | null | undefined;
  getConfigurationItem: (id: string) => { name: string } | null | undefined;
  getAsset: (id: string) => { tag: string; name: string } | null | undefined;
  getProblem: (id: string) => { number: string; title: string } | null | undefined;
  getChange: (id: string) => { number: string; title: string } | null | undefined;
  getKnowledgeArticle: (id: string) => { title?: string; titleKey?: string } | null | undefined;
} | null = null;
let mockStoreLoading = false;
let isMock = false;

async function ensureMockStore(): Promise<typeof mockStore> {
  if (mockStore) return mockStore;
  if (mockStoreLoading) return null;
  try {
    isMock = (await import('@/api/client')).isMockMode();
    if (!isMock) return null;
    mockStoreLoading = true;
    const mod = await import('@/mock/store');
    mockStore = mod;
    return mockStore;
  } catch {
    return null;
  }
}

// Synchronous version for callers that need immediate resolution
// Falls back to prefix-based labeling if mock store hasn't loaded yet
function getMockStoreSync() {
  return mockStore;
}

export function resolveRelatedLabel(id: string): string {
  if (!id) return id;

  const s = getMockStoreSync();
  if (s) {
    const wi = s.getWorkItem(id);
    if (wi) return `${wi.number} · ${truncate(wi.title)}`;

    const ci = s.getConfigurationItem(id);
    if (ci) return ci.name;

    const asset = s.getAsset(id);
    if (asset) return `${asset.tag} · ${asset.name}`;

    const problem = s.getProblem(id);
    if (problem) return `${problem.number} · ${truncate(problem.title)}`;

    const change = s.getChange(id);
    if (change) return `${change.number} · ${truncate(change.title)}`;

    const article = s.getKnowledgeArticle(id);
    if (article) {
      const title = (article.title ?? article.titleKey ?? id).trim();
      return truncate(title);
    }
  }

  // Prefix fallbacks
  if (id.startsWith('ci-')) return id.replace(/^ci-/, 'CI · ');
  if (id.startsWith('wi-')) return id.replace(/^wi-/, 'WI · ');
  if (id.startsWith('as-')) return id.replace(/^as-/, 'Asset · ');
  if (id.startsWith('pr-')) return id.replace(/^pr-/, 'PR · ');
  if (id.startsWith('ch-')) return id.replace(/^ch-/, 'CHG · ');
  if (id.startsWith('kb-')) return id.replace(/^kb-/, 'KB · ');
  return id;
}

export function resolveRelatedHref(id: string): string | undefined {
  if (!id) return undefined;
  const enc = encodeURIComponent(id);

  const s = getMockStoreSync();
  if (s) {
    if (s.getWorkItem(id)) return `/work-items/${id}`;
    if (s.getConfigurationItem(id)) return `/cmdb?ci=${enc}`;
    if (s.getProblem(id)) return `/problems?id=${enc}`;
    if (s.getChange(id)) return `/changes?id=${enc}`;
    if (s.getAsset(id)) return `/assets?id=${enc}`;
    if (s.getKnowledgeArticle(id)) return `/knowledge?article=${enc}`;
  }

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
  const s = getMockStoreSync();
  if (s) {
    if (s.getWorkItem(id)) return 'work_item';
    if (s.getConfigurationItem(id)) return 'ci';
    if (s.getAsset(id)) return 'asset';
    if (s.getProblem(id)) return 'problem';
    if (s.getChange(id)) return 'change';
    if (s.getKnowledgeArticle(id)) return 'knowledge';
  }

  if (id.startsWith('wi-')) return 'work_item';
  if (id.startsWith('ci-')) return 'ci';
  if (id.startsWith('as-')) return 'asset';
  if (id.startsWith('pr-')) return 'problem';
  if (id.startsWith('ch-')) return 'change';
  if (id.startsWith('kb-')) return 'knowledge';
  return 'unknown';
}

// Kick off async mock store load for mock mode
void ensureMockStore();
