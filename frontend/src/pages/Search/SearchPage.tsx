import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type KeyboardEvent as ReactKeyboardEvent,
} from 'react';
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
  Clock3,
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

const RECENTS_KEY = 'vox-search-recents';
const MAX_RECENTS = 8;

function typeLabel(t: (k: string) => string, objectType: string): string {
  const key = `search.types.${objectType}`;
  const translated = t(key);
  return translated === key ? objectType : translated;
}

function loadRecents(): string[] {
  try {
    const raw = localStorage.getItem(RECENTS_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((x): x is string => typeof x === 'string').slice(0, MAX_RECENTS);
  } catch {
    return [];
  }
}

function pushRecent(q: string): string[] {
  const needle = q.trim();
  if (!needle) return loadRecents();
  const next = [needle, ...loadRecents().filter((x) => x.toLowerCase() !== needle.toLowerCase())].slice(
    0,
    MAX_RECENTS,
  );
  try {
    localStorage.setItem(RECENTS_KEY, JSON.stringify(next));
  } catch {
    /* ignore */
  }
  return next;
}

function clearRecents(): void {
  try {
    localStorage.removeItem(RECENTS_KEY);
  } catch {
    /* ignore */
  }
}

function hitPath(hit: SearchHit): string | null {
  return resolveRelatedHref(hit.id) ?? searchHitPath(hit) ?? null;
}

export function SearchPage() {
  const t = useT();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const qParam = params.get('q') ?? '';
  const typesParam = params.get('types') ?? '';

  const [draft, setDraft] = useState(qParam);
  /** Full corpus hits (unfiltered by type) — source of facet counts. */
  const [allHits, setAllHits] = useState<SearchHit[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [recents, setRecents] = useState<string[]>(() => loadRecents());
  const [activeIndex, setActiveIndex] = useState(0);
  const listRef = useRef<HTMLUListElement>(null);

  const selectedTypes = useMemo(() => {
    if (!typesParam.trim()) return [] as string[];
    return typesParam
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
  }, [typesParam]);

  useEffect(() => {
    setDraft(qParam);
  }, [qParam]);

  const runSearch = useCallback(
    async (query: string, signal?: AbortSignal) => {
      const needle = query.trim();
      if (!needle) {
        setAllHits([]);
        setLoading(false);
        setError(null);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        // Always fetch full corpus for honest facet counts (S7 search residual)
        const result = await searchAll(needle, {
          limit: 50,
          signal,
        });
        if (!signal?.aborted) {
          setAllHits(result);
          setActiveIndex(0);
        }
      } catch (err) {
        if (signal?.aborted) return;
        setError(err instanceof Error ? err : new Error(String(err)));
        setAllHits(null);
      } finally {
        if (!signal?.aborted) setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    const controller = new AbortController();
    void runSearch(qParam, controller.signal);
    return () => controller.abort();
  }, [qParam, runSearch]);

  const commitQuery = (nextQ: string, nextTypes?: string[]) => {
    const types = nextTypes ?? selectedTypes;
    const next = new URLSearchParams();
    const trimmed = nextQ.trim();
    if (trimmed) {
      next.set('q', trimmed);
      setRecents(pushRecent(trimmed));
    }
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

  // Corpus facets: counts from full unfiltered result set
  const typeCounts = useMemo(() => {
    const map = new Map<string, number>();
    for (const h of allHits ?? []) {
      const k = (h.objectType || 'other').toLowerCase();
      map.set(k, (map.get(k) ?? 0) + 1);
    }
    return map;
  }, [allHits]);

  // Display hits: client filter by selected types
  const hits = useMemo(() => {
    if (!allHits) return null;
    if (!selectedTypes.length) return allHits;
    const allowed = new Set(selectedTypes.map((x) => x.toLowerCase()));
    return allHits.filter((h) => allowed.has((h.objectType || '').toLowerCase()));
  }, [allHits, selectedTypes]);

  useEffect(() => {
    setActiveIndex(0);
  }, [hits]);

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    commitQuery(draft);
  };

  const openHit = useCallback(
    (hit: SearchHit) => {
      const path = hitPath(hit);
      if (path) navigate(path);
    },
    [navigate],
  );

  // J/K keyboard navigation on result list
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!hits?.length) return;
      const tag = (e.target as HTMLElement)?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
        // Still allow J/K when not typing modifiers in empty-ish cases — skip when input focused
        if (tag === 'INPUT' || tag === 'TEXTAREA') return;
      }
      if (e.key === 'j' || e.key === 'J') {
        e.preventDefault();
        setActiveIndex((i) => Math.min(hits.length - 1, i + 1));
      } else if (e.key === 'k' || e.key === 'K') {
        e.preventDefault();
        setActiveIndex((i) => Math.max(0, i - 1));
      } else if (e.key === 'Enter' && !(e.target as HTMLElement)?.closest('form')) {
        const hit = hits[activeIndex];
        if (hit) {
          e.preventDefault();
          openHit(hit);
        }
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [hits, activeIndex, openHit]);

  useEffect(() => {
    const el = listRef.current?.querySelector(`[data-hit-index="${activeIndex}"]`);
    if (el && 'scrollIntoView' in el) {
      (el as HTMLElement).scrollIntoView({ block: 'nearest' });
    }
  }, [activeIndex]);

  const emptyQuery = !qParam.trim();
  const noResults =
    !loading && !error && hits && hits.length === 0 && !emptyQuery;

  const onDraftKeyDown = (e: ReactKeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown' && hits?.length) {
      e.preventDefault();
      setActiveIndex(0);
      listRef.current?.focus();
    }
  };

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
            <span className="chip chip--muted">{t('search.kbdHint')}</span>
          </div>
        )}
      </div>

      <form className="search-page__form" onSubmit={onSubmit} role="search">
        <Input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onDraftKeyDown}
          placeholder={t('search.placeholder')}
          aria-label={t('search.placeholder')}
          leading={<Search size={18} aria-hidden />}
          autoFocus
        />
        <Button type="submit" variant="primary">
          {t('app.search')}
        </Button>
      </form>

      {emptyQuery && recents.length > 0 && (
        <div className="search-recents" aria-label={t('search.recents')}>
          <div className="search-recents__head">
            <Clock3 size={14} aria-hidden />
            <b>{t('search.recents')}</b>
            <button
              type="button"
              className="text-link"
              onClick={() => {
                clearRecents();
                setRecents([]);
              }}
            >
              {t('search.recentsClear')}
            </button>
          </div>
          <div className="search-recents__chips">
            {recents.map((r) => (
              <button
                key={r}
                type="button"
                className="chip chip--toggle"
                onClick={() => commitQuery(r)}
              >
                {r}
              </button>
            ))}
          </div>
        </div>
      )}

      <div
        className="filter-chips search-page__chips"
        role="group"
        aria-label={t('search.filterByType')}
      >
        <button
          type="button"
          className={`chip chip--toggle${selectedTypes.length === 0 ? ' is-on' : ''}`}
          onClick={clearTypes}
        >
          {t('app.all')}
          {allHits && allHits.length > 0 && <b>{allHits.length}</b>}
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
            void runSearch(qParam);
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
        <ul
          ref={listRef}
          className="search-page__results"
          aria-label={t('search.results')}
          tabIndex={0}
        >
          {hits.map((hit, index) => {
            const path = hitPath(hit);
            const Icon = TYPE_ICONS[hit.objectType] ?? Search;
            const tone = TYPE_TONES[hit.objectType] ?? 'neutral';
            const snippet = hit.body?.trim();
            const active = index === activeIndex;
            return (
              <li
                key={`${hit.objectType}-${hit.id}`}
                className={`search-hit${active ? ' is-active' : ''}`}
                data-hit-index={index}
                onMouseEnter={() => setActiveIndex(index)}
              >
                <span className={`search-hit__icon search-hit__icon--${hit.objectType}`}>
                  <Icon size={18} aria-hidden />
                </span>
                <div className="search-hit__body">
                  <div className="search-hit__head">
                    <Badge tone={tone}>{typeLabel(t, hit.objectType)}</Badge>
                    {hit.updatedAt && (
                      <small className="muted">
                        {formatRelative(hit.updatedAt, t)}
                      </small>
                    )}
                  </div>
                  {path ? (
                    <Link to={path} className="search-hit__title">
                      {hit.title}
                    </Link>
                  ) : (
                    <b className="search-hit__title">{hit.title}</b>
                  )}
                  {snippet && <p className="search-hit__snippet">{snippet}</p>}
                </div>
                {path && (
                  <button
                    type="button"
                    className="icon-btn"
                    aria-label={t('app.open')}
                    onClick={() => openHit(hit)}
                  >
                    <ArrowRight size={16} />
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
