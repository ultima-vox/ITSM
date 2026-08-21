import { useCallback, useEffect, useMemo, useState } from 'react';
import { Bookmark, BookmarkPlus, ChevronDown } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { useT } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useWorkItemsSync } from '@/hooks/useWorkItemsSync';
import { useDensity } from '@/hooks/useDensity';
import { useToast } from '@/hooks/useToast';
import {
  createQueueSavedView,
  deleteQueueSavedView,
  fetchQueueSavedViews,
  fetchWorkItems,
  isMockMode,
} from '@/api';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import {
  isBreached,
  isEscalated,
  isMyGroup,
  isUnassigned,
} from '@/api/queuePredicates';
import { ErrorState, Select, Tabs } from '@/components/ui';
import { OperatorGrid } from '@/components/data-display';
import type { Priority, QueueSavedView, SlaState, WorkItemStatus, WorkItemType } from '@/types';

type QueueTab = 'unassigned' | 'mygroup' | 'escalated' | 'breached' | 'all';

const QUEUE_TABS: QueueTab[] = [
  'unassigned',
  'mygroup',
  'escalated',
  'breached',
  'all',
];

const SAVED_VIEWS_KEY = 'vox-queue-saved-views';

const DEFAULT_VIEWS: QueueSavedView[] = [
  {
    id: 'builtin-breach-critical',
    name: 'Breached + critical',
    tab: 'breached',
    priority: 'critical',
    type: '',
    status: '',
    sla: '',
    builtin: true,
  },
  {
    id: 'builtin-unassigned-high',
    name: 'Unassigned high+',
    tab: 'unassigned',
    priority: 'high',
    type: '',
    status: '',
    sla: '',
    builtin: true,
  },
];

function isQueueTab(v: string | null): v is QueueTab {
  return !!v && (QUEUE_TABS as string[]).includes(v);
}

function loadSavedViews(): QueueSavedView[] {
  try {
    const raw = localStorage.getItem(SAVED_VIEWS_KEY);
    if (!raw) return DEFAULT_VIEWS;
    const parsed = JSON.parse(raw) as QueueSavedView[];
    if (!Array.isArray(parsed) || parsed.length === 0) return DEFAULT_VIEWS;
    const custom = parsed.filter((v) => !v.builtin);
    return [...DEFAULT_VIEWS, ...custom];
  } catch {
    return DEFAULT_VIEWS;
  }
}

function persistCustomViews(views: QueueSavedView[]) {
  try {
    const custom = views.filter((v) => !v.builtin);
    localStorage.setItem(SAVED_VIEWS_KEY, JSON.stringify(custom));
  } catch {
    /* ignore */
  }
}

export function QueuesPage() {
  const t = useT();
  const { isCompact, toggleDensity } = useDensity();
  const { success, info, error: toastError } = useToast();
  const [params, setParams] = useSearchParams();
  const [savedViews, setSavedViews] = useState<QueueSavedView[]>(
    isMockMode() ? loadSavedViews() : DEFAULT_VIEWS,
  );
  const [viewsOpen, setViewsOpen] = useState(false);
  const currentUser = useCurrentUser();

  const tab: QueueTab = isQueueTab(params.get('tab'))
    ? (params.get('tab') as QueueTab)
    : 'all';
  const priority = params.get('priority') ?? '';
  const type = params.get('type') ?? '';
  const status = params.get('status') ?? '';
  const sla = params.get('sla') ?? '';

  const { data, loading, error, reload } = useAsync(() => fetchWorkItems(), []);
  useWorkItemsSync(reload);

  const setFilter = useCallback(
    (key: string, value: string) => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (!value) next.delete(key);
          else next.set(key, value);
          return next;
        },
        { replace: true },
      );
    },
    [setParams],
  );

  const setTab = useCallback(
    (id: string) => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (id === 'all') next.delete('tab');
          else next.set('tab', id);
          return next;
        },
        { replace: true },
      );
    },
    [setParams],
  );

  const applyView = useCallback(
    (view: QueueSavedView) => {
      setParams(
        () => {
          const next = new URLSearchParams();
          if (view.tab && view.tab !== 'all') next.set('tab', view.tab);
          if (view.priority) next.set('priority', view.priority);
          if (view.type) next.set('type', view.type);
          if (view.status) next.set('status', view.status);
          if (view.sla) next.set('sla', view.sla);
          return next;
        },
        { replace: true },
      );
      setViewsOpen(false);
      info(t('queues.viewApplied', { name: view.name }));
    },
    [setParams, info, t],
  );

  useEffect(() => {
    if (isMockMode()) return;
    let cancelled = false;
    fetchQueueSavedViews()
      .then((custom) => {
        if (!cancelled) setSavedViews([...DEFAULT_VIEWS, ...custom]);
      })
      .catch(() => {
        if (!cancelled) setSavedViews(DEFAULT_VIEWS);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const saveCurrentView = async () => {
    const name = window.prompt(t('queues.saveViewPrompt'));
    if (!name?.trim()) return;
    const draft: QueueSavedView = {
      id: `view-${Date.now()}`,
      name: name.trim(),
      tab,
      priority,
      type,
      status,
      sla,
    };
    if (isMockMode()) {
      const next = [...savedViews, draft];
      setSavedViews(next);
      persistCustomViews(next);
      success(t('queues.viewSaved', { name: draft.name }));
      return;
    }
    try {
      const created = await createQueueSavedView(draft);
      setSavedViews((current) => [...current, created]);
      success(t('queues.viewSaved', { name: created.name }));
    } catch {
      toastError(t('queues.viewSaveFailed'));
    }
  };

  const removeView = async (view: QueueSavedView) => {
    if (view.builtin) return;
    if (isMockMode()) {
      const next = savedViews.filter((item) => item.id !== view.id);
      setSavedViews(next);
      persistCustomViews(next);
      info(t('queues.viewDeleted', { name: view.name }));
      return;
    }
    try {
      await deleteQueueSavedView(view.id);
      setSavedViews((current) => current.filter((item) => item.id !== view.id));
      info(t('queues.viewDeleted', { name: view.name }));
    } catch {
      toastError(t('queues.viewDeleteFailed'));
    }
  };

  useEffect(() => {
    if (!viewsOpen) return;
    const onDoc = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (!target.closest?.('.saved-views')) setViewsOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [viewsOpen]);

  // Real queue predicates from store helpers
  const counts = useMemo(() => {
    const list = data ?? [];
    return {
      unassigned: list.filter(isUnassigned).length,
      mygroup: list.filter((w) => isMyGroup(w, currentUser.teamId)).length,
      escalated: list.filter(isEscalated).length,
      breached: list.filter(isBreached).length,
      all: list.length,
    };
  }, [data, currentUser.teamId]);

  const filtered = useMemo(() => {
    return (data ?? []).filter((w) => {
      if (tab === 'unassigned' && !isUnassigned(w)) return false;
      if (tab === 'mygroup' && !isMyGroup(w, currentUser.teamId)) return false;
      if (tab === 'escalated' && !isEscalated(w)) return false;
      if (tab === 'breached' && !isBreached(w)) return false;
      if (priority && w.priority !== (priority as Priority)) return false;
      if (type && w.type !== (type as WorkItemType)) return false;
      if (status && w.status !== (status as WorkItemStatus)) return false;
      if (sla && w.slaState !== (sla as SlaState)) return false;
      return true;
    });
  }, [data, tab, priority, type, status, sla, currentUser.teamId]);

  const reset = () => {
    setParams({}, { replace: true });
  };

  const hasActiveFilters = !!(priority || type || status || sla || tab !== 'all');

  const emptyTitle =
    tab === 'unassigned'
      ? t('queues.emptyUnassigned')
      : tab === 'mygroup'
        ? t('queues.emptyMyGroup')
        : tab === 'escalated'
          ? t('queues.emptyEscalated')
          : tab === 'breached'
            ? t('queues.emptyBreached')
            : t('queues.emptyTitle');

  const emptyHint =
    tab === 'unassigned'
      ? t('queues.emptyUnassignedHint')
      : tab === 'mygroup'
        ? t('queues.emptyMyGroupHint')
        : tab === 'escalated'
          ? t('queues.emptyEscalatedHint')
          : tab === 'breached'
            ? t('queues.emptyBreachedHint')
            : t('queues.emptyHint');

  const emptyAction =
    hasActiveFilters || tab !== 'all'
      ? t('queues.emptyAction')
      : t('queues.emptyActionAll');

  if (error && !loading && !data) {
    return (
      <section className="page page--queues">
        <div className="page-head">
          <div>
            <h1>{t('queues.title')}</h1>
            <p className="page-subtitle">{t('queues.subtitle')}</p>
          </div>
        </div>
        <ErrorState onRetry={reload} />
      </section>
    );
  }

  return (
    <section className="page page--queues">
      <div className="page-head page-head--tight">
        <div>
          <h1>{t('queues.title')}</h1>
          <p className="page-subtitle">{t('queues.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">{t('queues.showing', { n: filtered.length })}</span>
          <div className="saved-views">
            <button
              type="button"
              className="chip chip--toggle"
              aria-expanded={viewsOpen}
              aria-haspopup="listbox"
              onClick={() => setViewsOpen((v) => !v)}
            >
              <Bookmark size={13} aria-hidden />
              {t('queues.savedViews')}
              <ChevronDown size={13} aria-hidden />
            </button>
            {viewsOpen && (
              <div className="saved-views__menu" role="listbox">
                {savedViews.map((v) => (
                  <div key={v.id} className="saved-views__row">
                    <button
                      type="button"
                      role="option"
                      className="saved-views__apply"
                      onClick={() => applyView(v)}
                    >
                      <span>
                        <b>{v.name}</b>
                        {v.builtin && (
                          <small>{t('queues.builtinView')}</small>
                        )}
                      </span>
                    </button>
                    {!v.builtin && (
                      <button
                        type="button"
                        className="saved-views__delete"
                        aria-label={t('queues.deleteView', { name: v.name })}
                        onClick={() => void removeView(v)}
                      >
                        ×
                      </button>
                    )}
                  </div>
                ))}
                <button
                  type="button"
                  className="saved-views__save"
                  onClick={() => void saveCurrentView()}
                >
                  <BookmarkPlus size={14} aria-hidden />
                  {t('queues.saveCurrentView')}
                </button>
              </div>
            )}
          </div>
          <button
            type="button"
            className={`chip chip--toggle${isCompact ? ' is-on' : ''}`}
            onClick={toggleDensity}
          >
            {isCompact ? t('app.densityCompact') : t('app.densityComfortable')}
          </button>
          {hasActiveFilters && (
            <button type="button" className="text-link" onClick={reset}>
              {t('app.reset')}
            </button>
          )}
        </div>
      </div>

      <div className="queue-sla-strip" role="region" aria-label={t('queues.summary')}>
        <button
          type="button"
          className={`queue-sla-strip__pill${tab === 'breached' ? ' is-active' : ''}`}
          onClick={() => setTab('breached')}
        >
          <strong>{counts.breached}</strong>
          <span>{t('queues.tabBreached')}</span>
        </button>
        <button
          type="button"
          className={`queue-sla-strip__pill${tab === 'unassigned' ? ' is-active' : ''}`}
          onClick={() => setTab('unassigned')}
        >
          <strong>{counts.unassigned}</strong>
          <span>{t('queues.tabUnassigned')}</span>
        </button>
        <button
          type="button"
          className={`queue-sla-strip__pill${tab === 'escalated' ? ' is-active' : ''}`}
          onClick={() => setTab('escalated')}
        >
          <strong>{counts.escalated}</strong>
          <span>{t('queues.tabEscalated')}</span>
        </button>
        <button
          type="button"
          className={`queue-sla-strip__pill${tab === 'mygroup' ? ' is-active' : ''}`}
          onClick={() => setTab('mygroup')}
        >
          <strong>{counts.mygroup}</strong>
          <span>{t('queues.tabMyGroup')}</span>
        </button>
      </div>

      <Tabs
        value={tab}
        onChange={setTab}
        className="tabs--queue"
        items={[
          {
            id: 'unassigned',
            label: t('queues.tabUnassigned'),
            count: counts.unassigned,
            tone: counts.unassigned > 0 ? 'warn' : undefined,
          },
          {
            id: 'mygroup',
            label: t('queues.tabMyGroup'),
            count: counts.mygroup,
          },
          {
            id: 'escalated',
            label: t('queues.tabEscalated'),
            count: counts.escalated,
            tone: counts.escalated > 0 ? 'warn' : undefined,
          },
          {
            id: 'breached',
            label: t('queues.tabBreached'),
            count: counts.breached,
            tone: counts.breached > 0 ? 'danger' : undefined,
          },
          {
            id: 'all',
            label: t('queues.tabAll'),
            count: counts.all,
          },
        ]}
      />

      <div className="filters-bar filters-bar--queues">
        <Select
          label={t('queues.filterPriority')}
          value={priority}
          onChange={(e) => setFilter('priority', e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'critical', label: t('priority.critical') },
            { value: 'high', label: t('priority.high') },
            { value: 'medium', label: t('priority.medium') },
            { value: 'low', label: t('priority.low') },
          ]}
        />
        <Select
          label={t('queues.filterType')}
          value={type}
          onChange={(e) => setFilter('type', e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'incident', label: t('workItemType.incident') },
            { value: 'request', label: t('workItemType.request') },
            { value: 'change', label: t('workItemType.change') },
            { value: 'problem', label: t('workItemType.problem') },
          ]}
        />
        <Select
          label={t('queues.filterStatus')}
          value={status}
          onChange={(e) => setFilter('status', e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'new', label: t('status.new') },
            { value: 'in_progress', label: t('status.in_progress') },
            { value: 'waiting', label: t('status.waiting') },
            { value: 'resolved', label: t('status.resolved') },
          ]}
        />
        <Select
          label={t('queues.filterSla')}
          value={sla}
          onChange={(e) => setFilter('sla', e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'on_track', label: t('sla.on_track') },
            { value: 'at_risk', label: t('sla.at_risk') },
            { value: 'breached', label: t('sla.breached') },
            { value: 'met', label: t('sla.met') },
          ]}
        />
      </div>

      {error && data && (
        <div className="mb-2">
          <ErrorState onRetry={reload} />
        </div>
      )}

      <OperatorGrid
        items={filtered}
        loading={loading}
        emptyTitle={emptyTitle}
        emptyHint={emptyHint}
        emptyActionLabel={emptyAction}
        onEmptyAction={reset}
        showKeyboardHint
        showQueue
      />
    </section>
  );
}
