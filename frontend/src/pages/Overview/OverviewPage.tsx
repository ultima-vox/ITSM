import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Bell,
  Bot,
  Clock3,
  Command,
  MoreHorizontal,
  Plus,
  Sparkles,
  TicketCheck,
  CircleHelp,
  FilePlus2,
  ShieldAlert,
  AlertTriangle,
  ArrowRight,
  ListFilter,
  Loader2,
  SlidersHorizontal,
} from 'lucide-react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useT, useI18n } from '@/i18n';
import { useAuth } from '@/auth';
import { useShell } from '@/hooks/useShell';
import { useAsync } from '@/hooks/useAsync';
import { useWorkItemsSync } from '@/hooks/useWorkItemsSync';
import { useDensity } from '@/hooks/useDensity';
import { useToast } from '@/hooks/useToast';
import {
  buildQueueSummaryText,
  fetchDashboardMetrics,
  fetchWorkItems,
  summarizeCopilot,
  isMockMode,
} from '@/api';
import { currentUser } from '@/mock/data';
import { getQueueCopilotStats } from '@/mock/store';
import { Button, ErrorState, Skeleton, Toggle } from '@/components/ui';
import {
  MetricCard,
  DonutChart,
  OperatorGrid,
} from '@/components/data-display';
import { formatGreetingDate } from '@/lib/format';
import type { WorkItem } from '@/types';

const WIDGETS_KEY = 'vox-overview-widgets';

type WidgetKey = 'metrics' | 'queue' | 'copilot' | 'flow';

type OverviewWidgets = Record<WidgetKey, boolean>;

const DEFAULT_WIDGETS: OverviewWidgets = {
  metrics: true,
  queue: true,
  copilot: true,
  flow: true,
};

type QueueQuickFilter = 'all' | 'my' | 'unassigned' | 'breached';

function loadWidgets(): OverviewWidgets {
  try {
    const raw = localStorage.getItem(WIDGETS_KEY);
    if (!raw) return { ...DEFAULT_WIDGETS };
    const parsed = JSON.parse(raw) as Partial<OverviewWidgets>;
    return {
      metrics:
        typeof parsed.metrics === 'boolean'
          ? parsed.metrics
          : DEFAULT_WIDGETS.metrics,
      queue:
        typeof parsed.queue === 'boolean' ? parsed.queue : DEFAULT_WIDGETS.queue,
      copilot:
        typeof parsed.copilot === 'boolean'
          ? parsed.copilot
          : DEFAULT_WIDGETS.copilot,
      flow: typeof parsed.flow === 'boolean' ? parsed.flow : DEFAULT_WIDGETS.flow,
    };
  } catch {
    return { ...DEFAULT_WIDGETS };
  }
}

function persistWidgets(widgets: OverviewWidgets) {
  try {
    localStorage.setItem(WIDGETS_KEY, JSON.stringify(widgets));
  } catch {
    /* ignore quota / private mode */
  }
}

function firstName(full: string): string {
  const trimmed = full.trim();
  if (!trimmed) return full;
  return trimmed.split(/\s+/)[0] ?? trimmed;
}

function matchesMyWork(w: WorkItem): boolean {
  return w.assignee?.id === currentUser.id;
}

export function OverviewPage() {
  const t = useT();
  const { locale } = useI18n();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { openCreate, openCommand } = useShell();
  const { info } = useToast();
  const { isAuthenticated, user } = useAuth();
  const { isCompact } = useDensity();
  const metrics = useAsync(() => fetchDashboardMetrics(), []);
  const items = useAsync(() => fetchWorkItems(), []);
  useWorkItemsSync(metrics.reload, items.reload);
  const [createOpen, setCreateOpen] = useState(false);
  const createRef = useRef<HTMLDivElement>(null);
  const [widgetsOpen, setWidgetsOpen] = useState(false);
  const widgetsRef = useRef<HTMLDivElement>(null);
  const [widgets, setWidgets] = useState<OverviewWidgets>(loadWidgets);
  const [queueFilter, setQueueFilter] = useState<QueueQuickFilter>('all');
  const [copilotText, setCopilotText] = useState<string | null>(null);
  const [copilotLoading, setCopilotLoading] = useState(false);
  const [copilotError, setCopilotError] = useState<string | null>(null);
  const copilotRanFromQuery = useRef(false);

  const setWidget = useCallback((key: WidgetKey, on: boolean) => {
    setWidgets((prev) => {
      const next = { ...prev, [key]: on };
      persistWidgets(next);
      return next;
    });
  }, []);

  useEffect(() => {
    if (!createOpen) return;
    const onDoc = (e: MouseEvent) => {
      if (!createRef.current?.contains(e.target as Node)) setCreateOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setCreateOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [createOpen]);

  useEffect(() => {
    if (!widgetsOpen) return;
    const onDoc = (e: MouseEvent) => {
      if (!widgetsRef.current?.contains(e.target as Node)) setWidgetsOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setWidgetsOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [widgetsOpen]);

  const sorted = useMemo(() => {
    const list = [...(items.data ?? [])];
    const rank: Record<string, number> = {
      breached: 0,
      at_risk: 1,
      on_track: 2,
      met: 3,
    };
    list.sort((a, b) => {
      const sa = rank[a.slaState] ?? 9;
      const sb = rank[b.slaState] ?? 9;
      if (sa !== sb) return sa - sb;
      return b.updatedAt.localeCompare(a.updatedAt);
    });
    return list;
  }, [items.data]);

  const visible = useMemo(() => {
    if (queueFilter === 'my') return sorted.filter(matchesMyWork);
    if (queueFilter === 'unassigned') return sorted.filter((w) => !w.assignee);
    if (queueFilter === 'breached')
      return sorted.filter((w) => w.slaState === 'breached');
    return sorted;
  }, [sorted, queueFilter]);

  const queueFilterCounts = useMemo(() => {
    const list = items.data ?? [];
    return {
      my: list.filter(matchesMyWork).length,
      unassigned: list.filter((w) => !w.assignee).length,
      breached: list.filter((w) => w.slaState === 'breached').length,
    };
  }, [items.data]);

  const slaCounts = useMemo(() => {
    const list = items.data ?? [];
    return {
      breached: list.filter((w) => w.slaState === 'breached').length,
      atRisk: list.filter((w) => w.slaState === 'at_risk').length,
      unassigned: list.filter((w) => !w.assignee).length,
    };
  }, [items.data]);

  const topUrgent = useMemo(
    () =>
      sorted
        .filter((w) => w.slaState === 'breached' || w.slaState === 'at_risk')
        .slice(0, 2),
    [sorted],
  );

  const flow = metrics.data?.flow;
  const totalActive = flow
    ? flow.new + flow.inProgress + flow.waiting
    : metrics.data?.open ?? 0;

  const greetingName =
    isAuthenticated && user?.name?.trim()
      ? firstName(user.name)
      : null;
  const greeting = greetingName
    ? t('overview.greetingNamed', { name: greetingName })
    : t('overview.greeting');

  const showRail = widgets.flow || widgets.copilot;
  const showDashboard = widgets.queue || showRail;

  const runCopilotSummarize = useCallback(async () => {
    setCopilotLoading(true);
    setCopilotError(null);
    try {
      let content: string | undefined;
      if (!isMockMode()) {
        const list = items.data ?? [];
        content = buildQueueSummaryText({
          open: list.filter(
            (w) =>
              w.status !== 'resolved' &&
              w.status !== 'closed' &&
              w.status !== 'cancelled',
          ).length,
          breached: list.filter((w) => w.slaState === 'breached').length,
          atRisk: list.filter((w) => w.slaState === 'at_risk').length,
          unassigned: list.filter((w) => !w.assignee).length,
          critical: list.filter((w) => w.priority === 'critical').length,
          topBreached: list
            .filter((w) => w.slaState === 'breached')
            .slice(0, 3)
            .map((w) => ({
              number: w.number,
              title: w.title,
              slaTarget: w.slaTarget,
            })),
        });
      } else {
        content = buildQueueSummaryText(getQueueCopilotStats());
      }
      const res = await summarizeCopilot({ content });
      setCopilotText(res.content);
    } catch {
      setCopilotError(t('overview.copilotError'));
      setCopilotText(null);
    } finally {
      setCopilotLoading(false);
    }
  }, [items.data, t]);

  useEffect(() => {
    if (searchParams.get('copilot') !== '1') return;
    if (copilotRanFromQuery.current) return;
    copilotRanFromQuery.current = true;
    void runCopilotSummarize();
    const next = new URLSearchParams(searchParams);
    next.delete('copilot');
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams, runCopilotSummarize]);

  const runSuggestion = (kind: 'urgent' | 'sla' | 'unassigned' | 'brief') => {
    if (kind === 'urgent') {
      navigate('/queues?tab=breached');
      info(t('overview.copilotNavBreached'));
      return;
    }
    if (kind === 'sla') {
      navigate('/queues?sla=at_risk');
      info(t('overview.copilotNavAtRisk'));
      return;
    }
    if (kind === 'unassigned') {
      navigate('/queues?tab=unassigned');
      info(t('overview.copilotNavUnassigned'));
      return;
    }
    void runCopilotSummarize();
  };

  const toggleQueueFilter = (f: Exclude<QueueQuickFilter, 'all'>) => {
    setQueueFilter((prev) => (prev === f ? 'all' : f));
  };

  return (
    <section className="page page--overview">
      <div className={`headline${isCompact ? ' headline--compact' : ''}`}>
        <div>
          <p className="eyebrow">
            {t('overview.eyebrow', { date: formatGreetingDate(locale) })}
          </p>
          <h1>{greeting}</h1>
          {!isCompact && (
            <p className="page-subtitle">{t('overview.subtitle')}</p>
          )}
        </div>
        <div className="headline__actions">
          <div className="headline__widgets" ref={widgetsRef}>
            <Button
              variant="ghost"
              icon={<SlidersHorizontal size={16} />}
              onClick={() => setWidgetsOpen((v) => !v)}
              aria-expanded={widgetsOpen}
              aria-haspopup="menu"
              aria-label={t('overview.customize')}
            >
              {isCompact ? null : t('overview.customize')}
            </Button>
            {widgetsOpen && (
              <div
                className="widgets-popover"
                role="menu"
                aria-label={t('overview.customize')}
              >
                <p className="widgets-popover__title">
                  {t('overview.widgetsTitle')}
                </p>
                {(
                  [
                    ['metrics', 'overview.widgetMetrics'],
                    ['queue', 'overview.widgetQueue'],
                    ['flow', 'overview.widgetFlow'],
                    ['copilot', 'overview.widgetCopilot'],
                  ] as const
                ).map(([key, labelKey]) => (
                  <div key={key} className="widgets-popover__row" role="menuitem">
                    <Toggle
                      id={`overview-widget-${key}`}
                      checked={widgets[key]}
                      onChange={(on) => setWidget(key, on)}
                      label={t(labelKey)}
                    />
                  </div>
                ))}
              </div>
            )}
          </div>
          <div ref={createRef} style={{ position: 'relative' }}>
            <Button
              variant="primary"
              icon={<Plus size={18} />}
              onClick={() => setCreateOpen((v) => !v)}
              aria-expanded={createOpen}
              aria-haspopup="menu"
            >
              {t('app.create')}
            </Button>
            {createOpen && (
              <div className="create-popover" role="menu">
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    openCreate('incident');
                    setCreateOpen(false);
                  }}
                >
                  <span className="create-icon create-icon--incident">
                    <CircleHelp size={16} />
                  </span>
                  <span>
                    <b>{t('header.createIncident')}</b>
                    <small>{t('header.createIncidentHint')}</small>
                  </span>
                </button>
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    openCreate('request');
                    setCreateOpen(false);
                  }}
                >
                  <span className="create-icon create-icon--request">
                    <FilePlus2 size={16} />
                  </span>
                  <span>
                    <b>{t('header.createRequest')}</b>
                    <small>{t('header.createRequestHint')}</small>
                  </span>
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* SLA urgency strip — operator signal, not marketing */}
      {!items.loading && (slaCounts.breached > 0 || slaCounts.atRisk > 0) && (
        <div
          className="sla-urgency"
          role="region"
          aria-label={t('overview.urgencyTitle')}
        >
          <div className="sla-urgency__lead">
            <ShieldAlert size={16} aria-hidden />
            <div>
              <b>{t('overview.urgencyTitle')}</b>
              <span>{t('overview.urgencyHint')}</span>
            </div>
          </div>
          <div className="sla-urgency__stats">
            <Link
              to="/queues?tab=breached"
              className={`sla-urgency__pill sla-urgency__pill--breach${
                slaCounts.breached === 0 ? ' is-quiet' : ''
              }`}
            >
              <strong>{slaCounts.breached}</strong>
              <span>{t('sla.breached')}</span>
            </Link>
            <Link
              to="/queues?sla=at_risk"
              className={`sla-urgency__pill sla-urgency__pill--risk${
                slaCounts.atRisk === 0 ? ' is-quiet' : ''
              }`}
            >
              <strong>{slaCounts.atRisk}</strong>
              <span>{t('sla.at_risk')}</span>
            </Link>
            <Link
              to="/queues?tab=unassigned"
              className="sla-urgency__pill sla-urgency__pill--neutral"
            >
              <strong>{slaCounts.unassigned}</strong>
              <span>{t('queues.tabUnassigned')}</span>
            </Link>
          </div>
          {topUrgent.length > 0 && (
            <div className="sla-urgency__items">
              {topUrgent.map((w) => (
                <Link
                  key={w.id}
                  to={`/work-items/${w.id}`}
                  className="sla-urgency__item"
                >
                  {w.slaState === 'breached' ? (
                    <ShieldAlert size={13} aria-hidden />
                  ) : (
                    <AlertTriangle size={13} aria-hidden />
                  )}
                  <b className="mono accent">{w.number}</b>
                  <span>{w.title}</span>
                  <em>{w.slaTarget}</em>
                </Link>
              ))}
            </div>
          )}
          <Link to="/queues?tab=breached" className="sla-urgency__cta">
            {t('overview.urgencyOpen')}
            <ArrowRight size={14} aria-hidden />
          </Link>
        </div>
      )}

      {widgets.metrics && (
        <div className="metrics-grid">
          {metrics.loading ? (
            Array.from({ length: 4 }).map((_, i) => (
              <div className="metric-card metric-card--skeleton" key={i}>
                <Skeleton width={36} height={36} radius={10} />
                <div style={{ flex: 1 }}>
                  <Skeleton width="40%" height={10} />
                  <Skeleton width="30%" height={20} />
                  <Skeleton width="55%" height={10} />
                </div>
              </div>
            ))
          ) : metrics.error ? (
            <div className="panel" style={{ gridColumn: '1 / -1' }}>
              <ErrorState onRetry={metrics.reload} />
            </div>
          ) : metrics.data ? (
            <>
              <MetricCard
                icon={<TicketCheck size={18} />}
                color="violet"
                value={String(metrics.data.open)}
                label={t('overview.open')}
                detail={t('overview.openDetail', { n: metrics.data.openDelta })}
              />
              <MetricCard
                icon={<Clock3 size={18} />}
                color="amber"
                value={String(metrics.data.dueToday)}
                label={t('overview.due')}
                detail={t('overview.dueDetail', { n: metrics.data.dueUrgent })}
              />
              <MetricCard
                icon={<Bell size={18} />}
                color="rose"
                value={String(metrics.data.breached)}
                label={t('overview.breached')}
                detail={t('overview.breachedDetail')}
              />
              <MetricCard
                icon={<Sparkles size={18} />}
                color="mint"
                value={`${metrics.data.satisfaction}%`}
                label={t('overview.satisfaction')}
                detail={t('overview.satisfactionDetail')}
              />
            </>
          ) : null}
        </div>
      )}

      {showDashboard && (
        <div
          className={`dashboard-grid${
            !widgets.queue || !showRail ? ' dashboard-grid--single' : ''
          }`}
        >
          {widgets.queue && (
            <section className="panel panel--overview-queue">
              <div className="panel__header panel__header--dense">
                <div>
                  <h2>{t('overview.workQueue')}</h2>
                  <p>
                    {t('overview.workQueueHint', {
                      shown: Math.min(visible.length, 8),
                      total:
                        queueFilter === 'all'
                          ? metrics.data?.open ?? visible.length
                          : visible.length,
                    })}
                  </p>
                </div>
                <Link to="/queues" className="text-link">
                  {t('overview.viewAll')} <span aria-hidden>→</span>
                </Link>
              </div>

              <div
                className="overview-queue-filters"
                role="toolbar"
                aria-label={t('overview.queueFilters')}
              >
                <button
                  type="button"
                  className={`chip chip--toggle${
                    queueFilter === 'my' ? ' is-on' : ''
                  }`}
                  onClick={() => toggleQueueFilter('my')}
                  aria-pressed={queueFilter === 'my'}
                >
                  {t('overview.filterMy')}
                  <b>{queueFilterCounts.my}</b>
                </button>
                <button
                  type="button"
                  className={`chip chip--toggle${
                    queueFilter === 'unassigned' ? ' is-on' : ''
                  }`}
                  onClick={() => toggleQueueFilter('unassigned')}
                  aria-pressed={queueFilter === 'unassigned'}
                >
                  {t('overview.filterUnassigned')}
                  <b>{queueFilterCounts.unassigned}</b>
                </button>
                <button
                  type="button"
                  className={`chip chip--toggle${
                    queueFilter === 'breached' ? ' is-on' : ''
                  }`}
                  onClick={() => toggleQueueFilter('breached')}
                  aria-pressed={queueFilter === 'breached'}
                >
                  {t('overview.filterBreached')}
                  <b>{queueFilterCounts.breached}</b>
                </button>
              </div>

              {items.error && !items.data ? (
                <ErrorState onRetry={items.reload} />
              ) : (
                <OperatorGrid
                  items={visible}
                  loading={items.loading}
                  emptyTitle={t('overview.emptyQueue')}
                  emptyHint={t('app.noResultsHint')}
                  emptyActionLabel={
                    queueFilter !== 'all'
                      ? t('overview.clearQueueFilter')
                      : t('overview.viewAll')
                  }
                  onEmptyAction={() => {
                    if (queueFilter !== 'all') setQueueFilter('all');
                    else navigate('/queues');
                  }}
                  showQueue
                  showKeyboardHint={false}
                  compact
                  limit={8}
                />
              )}
            </section>
          )}

          {showRail && (
            <aside className="right-rail">
              {widgets.flow && (
                <section className="panel flow-panel">
                  <div className="panel__header panel__header--dense">
                    <div>
                      <h2>{t('overview.flow')}</h2>
                      <p>{t('overview.flowHint')}</p>
                    </div>
                    <button
                      type="button"
                      className="icon-btn"
                      aria-label={t('app.more')}
                    >
                      <MoreHorizontal size={18} />
                    </button>
                  </div>
                  {flow && (
                    <>
                      <DonutChart
                        slices={[
                          { value: flow.new, color: '#7560de' },
                          { value: flow.inProgress, color: '#5ac8c8' },
                          { value: flow.waiting, color: '#f5bc59' },
                        ]}
                        centerValue={totalActive}
                        centerLabel={t('overview.active')}
                      />
                      <div className="legend">
                        <p>
                          <i className="legend__dot legend__dot--1" />
                          {t('overview.flowNew')} <b>{flow.new}</b>
                        </p>
                        <p>
                          <i className="legend__dot legend__dot--2" />
                          {t('overview.flowProgress')} <b>{flow.inProgress}</b>
                        </p>
                        <p>
                          <i className="legend__dot legend__dot--3" />
                          {t('overview.flowWaiting')} <b>{flow.waiting}</b>
                        </p>
                      </div>
                    </>
                  )}
                </section>
              )}

              {widgets.copilot && (
                <section className="copilot">
                  <div className="copilot__top">
                    <span className="copilot__bot">
                      <Bot size={18} />
                    </span>
                    <div>
                      <h2>{t('overview.assistant')}</h2>
                      <span>
                        <i /> {t('app.online')}
                      </span>
                    </div>
                  </div>
                  <p className="copilot__brief">
                    {t('overview.assistantBrief', {
                      breached: slaCounts.breached,
                      atRisk: slaCounts.atRisk,
                      unassigned: slaCounts.unassigned,
                    })}
                  </p>
                  {(copilotLoading || copilotText || copilotError) && (
                    <div
                      className="copilot__response"
                      role="status"
                      aria-live="polite"
                    >
                      {copilotLoading ? (
                        <p className="copilot__response-loading">
                          <Loader2 size={14} className="spin" aria-hidden />
                          {t('overview.copilotThinking')}
                        </p>
                      ) : copilotError ? (
                        <p className="copilot__response-error">{copilotError}</p>
                      ) : (
                        <p className="copilot__response-text">{copilotText}</p>
                      )}
                    </div>
                  )}
                  <div className="copilot__actions">
                    <button
                      type="button"
                      className="copilot__action"
                      onClick={() => runSuggestion('brief')}
                      disabled={copilotLoading}
                    >
                      <Sparkles size={14} aria-hidden />
                      <span>
                        <b>{t('overview.suggestionBrief')}</b>
                        <small>{t('overview.suggestionBriefMeta')}</small>
                      </span>
                      <ArrowRight size={14} aria-hidden />
                    </button>
                    <button
                      type="button"
                      className="copilot__action"
                      onClick={() => runSuggestion('urgent')}
                    >
                      <ShieldAlert size={14} aria-hidden />
                      <span>
                        <b>{t('overview.suggestionUrgent')}</b>
                        <small>
                          {t('overview.suggestionUrgentMeta', {
                            n: slaCounts.breached,
                          })}
                        </small>
                      </span>
                      <ArrowRight size={14} aria-hidden />
                    </button>
                    <button
                      type="button"
                      className="copilot__action"
                      onClick={() => runSuggestion('sla')}
                    >
                      <AlertTriangle size={14} aria-hidden />
                      <span>
                        <b>{t('overview.suggestionSla')}</b>
                        <small>
                          {t('overview.suggestionSlaMeta', {
                            n: slaCounts.atRisk,
                          })}
                        </small>
                      </span>
                      <ArrowRight size={14} aria-hidden />
                    </button>
                    <button
                      type="button"
                      className="copilot__action"
                      onClick={() => runSuggestion('unassigned')}
                    >
                      <ListFilter size={14} aria-hidden />
                      <span>
                        <b>{t('overview.suggestionUnassigned')}</b>
                        <small>
                          {t('overview.suggestionUnassignedMeta', {
                            n: slaCounts.unassigned,
                          })}
                        </small>
                      </span>
                      <ArrowRight size={14} aria-hidden />
                    </button>
                  </div>
                  <button
                    type="button"
                    className="copilot__ask copilot__ask--btn"
                    onClick={() => openCommand()}
                  >
                    <span>{t('overview.ask')}</span>
                    <Command size={15} aria-hidden />
                  </button>
                </section>
              )}
            </aside>
          )}
        </div>
      )}
    </section>
  );
}
