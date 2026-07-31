import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  ArrowRight,
  BookOpen,
  Boxes,
  GitBranch,
  Package,
  Search,
  TicketCheck,
  AlertOctagon,
} from 'lucide-react';
import { useT } from '@/i18n';
import {
  searchAll,
  searchHitPath,
  SEARCH_OBJECT_TYPES,
  type SearchHit,
} from '@/api';
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Input,
  Skeleton,
} from '@/components/ui';
import { formatRelative } from '@/lib/format';
import { resolveRelatedHref } from '@/lib/resolveRelated';

const TYPE_ICONS: Record<string, typeof Search> = {
  'work-item': TicketCheck,
  knowledge: BookOpen,
  ci: Boxes,
  asset: Package,
  problem: AlertOctagon,
  change: GitBranch,
};

const TYPE_TONES: Record<
  string,
  'violet' | 'mint' | 'amber' | 'rose' | 'blue' | 'neutral'
> = {
  'work-item': 'violet',
  knowledge: 'mint',
  ci: 'blue',
  asset: 'amber',
  problem: 'rose',
  change: 'blue',
};

function typeLabel(t: (k: string) => string, objectType: string): string {
  const key = `search.types.${objectType}`;
  const translated = t(key);
  return translated === key ? objectType : translated;
}

export function SearchPage() {
  const t = useT();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const qParam = params.get('q') ?? '';
  const typesParam = params.get('types') ?? '';

  const [draft, setDraft] = useState(qParam);
  const [hits, setHits] = useState<SearchHit[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const selectedTypes = useMemo(() => {
    if (!typesParam.trim()) return [] as string[];
    return typesParam
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
  }, [typesParam]);

  // Keep input in sync when navigating with a new ?q=
  useEffect(() => {
    setDraft(qParam);
  }, [qParam]);

  const runSearch = useCallback(
    async (query: string, types: string[], signal?: AbortSignal) => {
      const needle = query.trim();
      if (!needle) {
        setHits([]);
        setLoading(false);
        setError(null);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const result = await searchAll(needle, {
          limit: 50,
          signal,
          objectTypes: types.length ? types : undefined,
        });
        if (!signal?.aborted) setHits(result);
      } catch (err) {
        if (signal?.aborted) return;
        setError(err instanceof Error ? err : new Error(String(err)));
        setHits(null);
      } finally {
        if (!signal?.aborted) setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    const controller = new AbortController();
    void runSearch(qParam, selectedTypes, controller.signal);
    return () => controller.abort();
  }, [qParam, selectedTypes, runSearch]);

  const commitQuery = (nextQ: string, nextTypes?: string[]) => {
    const types = nextTypes ?? selectedTypes;
    const next = new URLSearchParams();
    const trimmed = nextQ.trim();
    if (trimmed) next.set('q', trimmed);
    if (types.length) next.set('types', types.join(','));
    setParams(next, { replace: false });
  };

  const toggleType = (type: string) => {
    const set = new Set(selectedTypes);
    if (set.has(type)) set.delete(type);
    else set.add(type);
    commitQuery(qParam || draft, [...set]);
  };

  const clearTypes = () => commitQuery(qParam || draft, []);

  const typeCounts = useMemo(() => {
    const map = new Map<string, number>();
    for (const h of hits ?? []) {
      const k = (h.objectType || 'other').toLowerCase();
      map.set(k, (map.get(k) ?? 0) + 1);
    }
    return map;
  }, [hits]);

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    commitQuery(draft);
  };

  const emptyQuery = !qParam.trim();
  const noResults = !loading && !error && hits && hits.length === 0 && !emptyQuery;

  return (
    <section className="page page--search">
      <div className="page-head">
        <div>
          <h1>{t('search.title')}</h1>
          <p className="page-subtitle">{t('search.subtitle')}</p>
        </div>
        {!emptyQuery && hits && !loading && (
          <div className="page-head__meta">
            <span className="chip chip--muted">
              {t('search.resultCount', { n: hits.length })}
            </span>
          </div>
        )}
      </div>

      <form className="search-page__form" onSubmit={onSubmit} role="search">
        <Input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={t('search.placeholder')}
          aria-label={t('search.placeholder')}
          leading={<Search size={18} aria-hidden />}
          autoFocus
        />
        <Button type="submit" variant="primary">
          {t('app.search')}
        </Button>
      </form>

      <div className="filter-chips search-page__chips" role="group" aria-label={t('search.filterByType')}>
        <button
          type="button"
          className={`chip chip--toggle${selectedTypes.length === 0 ? ' is-on' : ''}`}
          onClick={clearTypes}
        >
          {t('app.all')}
        </button>
        {SEARCH_OBJECT_TYPES.map((type) => {
          const on = selectedTypes.includes(type);
          const count = typeCounts.get(type);
          return (
            <button
              key={type}
              type="button"
              className={`chip chip--toggle${on ? ' is-on' : ''}`}
              onClick={() => toggleType(type)}
              aria-pressed={on}
            >
              {typeLabel(t, type)}
              {count != null && count > 0 && <b>{count}</b>}
            </button>
          );
        })}
      </div>

      {emptyQuery && (
        <EmptyState
          title={t('search.emptyQueryTitle')}
          description={t('search.emptyQueryHint')}
          icon={<Search size={22} />}
        />
      )}

      {error && (
        <ErrorState
          onRetry={() => {
            void runSearch(qParam, selectedTypes);
          }}
        />
      )}

      {loading && (
        <div className="search-page__results" aria-busy="true" aria-live="polite">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="search-hit skeleton-block">
              <Skeleton height={18} width="40%" />
              <Skeleton height={14} width="70%" className="mt-2" />
              <Skeleton height={12} width="50%" className="mt-2" />
            </div>
          ))}
        </div>
      )}

      {noResults && (
        <EmptyState
          title={t('search.emptyTitle')}
          description={t('search.emptyHint')}
          actionLabel={t('app.clearSearch')}
          onAction={() => {
            setDraft('');
            setParams(new URLSearchParams(), { replace: false });
          }}
        />
      )}

      {!loading && !error && hits && hits.length > 0 && (
        <ul className="search-page__results" aria-label={t('search.results')}>
          {hits.map((hit) => {
            // Prefer resolveRelatedHref (entity deep-links); type fallback via searchHitPath
            const path =
              resolveRelatedHref(hit.id) ?? searchHitPath(hit) ?? null;
            const Icon = TYPE_ICONS[hit.objectType] ?? Search;
            const tone = TYPE_TONES[hit.objectType] ?? 'neutral';
            const snippet = hit.body?.trim();
            return (
              <li key={`${hit.objectType}-${hit.id}`} className="search-hit">
                <span className={`search-hit__icon search-hit__icon--${hit.objectType}`}>
                  <Icon size={18} aria-hidden />
                </span>
                <div className="search-hit__body">
                  <div className="search-hit__head">
                    <Badge tone={tone}>{typeLabel(t, hit.objectType)}</Badge>
                    {hit.updatedAt && (
                      <small className="muted">{formatRelative(hit.updatedAt, t)}</small>
                    )}
                  </div>
                  <h3 className="search-hit__title">
                    {path ? (
                      <Link to={path}>{hit.title || hit.id}</Link>
                    ) : (
                      hit.title || hit.id
                    )}
                  </h3>
                  {snippet && (
                    <p className="search-hit__snippet">
                      {snippet.length > 180 ? `${snippet.slice(0, 180)}…` : snippet}
                    </p>
                  )}
                </div>
                {path && (
                  <Button
                    variant="secondary"
                    size="sm"
                    icon={<ArrowRight size={14} />}
                    onClick={() => navigate(path)}
                  >
                    {t('app.open')}
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
