import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowRight,
  Clock3,
  Download,
  Gauge,
  ShieldAlert,
  TicketCheck,
  Layers,
  Activity,
  TrendingUp,
  Percent,
  Timer,
  Users,
  Printer,
  ShieldCheck,
  Share2,
  Package,
  Bug,
  Rocket,
} from 'lucide-react';
import { formatMinutes } from '@/api/worklogs';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useWorkItemsSync } from '@/hooks/useWorkItemsSync';
import {
  fetchDashboardMetrics,
  fetchWorkItems,
  fetchWorkloadReport,
  isMockMode,
  type WorkloadReport,
} from '@/api';
import { getActivities } from '@/mock/store';
import type { Priority, WorkItem, WorkItemStatus, WorkItemType } from '@/types';
import { Button, ErrorState, Select, Skeleton } from '@/components/ui';
import { MetricCard, PriorityBadge, StatusChip } from '@/components/data-display';

const PRIORITIES: Priority[] = ['critical', 'high', 'medium', 'low'];
const STATUSES: WorkItemStatus[] = [
  'new',
  'in_progress',
  'waiting',
  'resolved',
  'closed',
  'cancelled',
];
const TYPES: WorkItemType[] = ['incident', 'request', 'change', 'problem'];

function dayKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function startOfLocalDay(d: Date): Date {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x;
}

/** Last 7 local days including today, oldest → newest. */
function last7DayKeys(now = new Date()): { key: string; label: string; date: Date }[] {
  const end = startOfLocalDay(now);
  const out: { key: string; label: string; date: Date }[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(end);
    d.setDate(end.getDate() - i);
    out.push({
      key: dayKey(d),
      label: d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' }),
      date: d,
    });
  }
  return out;
}

function isResolvedStatus(s: WorkItemStatus): boolean {
  return s === 'resolved' || s === 'closed';
}

function isActiveStatus(s: WorkItemStatus): boolean {
  return s === 'new' || s === 'in_progress' || s === 'waiting';
}

function countFrom(map: Record<string, number> | undefined, ...keys: string[]): number {
  if (!map) return 0;
  let total = 0;
  for (const key of keys) {
    const needle = key.toUpperCase();
    for (const [entry, value] of Object.entries(map)) {
      if (entry.toUpperCase() === needle) total += value;
    }
  }
  return total;
}

function overlayLiveSnapshot(
  derived: {
    resolved: number;
    active: number;
    breached: number;
    atRisk: number;
    unassigned: number;
    total: number;
    byPriority: Record<Priority, number>;
    byStatus: Record<WorkItemStatus, number>;
    byType: { incident: number; request: number; change: number; problem: number };
    maxPriority: number;
    maxStatus: number;
    resolutionRate: number;
    mttrHours: number;
  },
  report: WorkloadReport,
) {
  const byPriority = Object.fromEntries(
    PRIORITIES.map((p) => [p, countFrom(report.byPriority, p)]),
  ) as Record<Priority, number>;
  const byStatus = Object.fromEntries(
    STATUSES.map((s) => [s, countFrom(report.byState, s)]),
  ) as Record<WorkItemStatus, number>;
  const byType = {
    incident: countFrom(report.byType, 'incident'),
    request: countFrom(report.byType, 'request', 'service_request'),
    change: countFrom(report.byType, 'change'),
    problem: countFrom(report.byType, 'problem'),
  };
  const active = report.open;
  const resolved = report.resolved;
  const denom = resolved + active;
  return {
    ...derived,
    active,
    resolved,
    breached: report.breached,
    atRisk: report.atRisk,
    unassigned: report.unassigned,
    total: Object.values(report.byState).reduce((sum, n) => sum + n, 0),
    byPriority,
    byStatus,
    byType,
    maxPriority: Math.max(1, ...Object.values(byPriority)),
    maxStatus: Math.max(1, ...Object.values(byStatus)),
    resolutionRate: denom === 0 ? 0 : Math.round((resolved / denom) * 100),
    mttrHours: report.mttrHours ?? derived.mttrHours,
  };
}

function csvEscape(value: string): string {
  if (/[",\n\r]/.test(value)) return `"${value.replace(/"/g, '""')}"`;
  return value;
}

function downloadWorkItemsCsv(items: WorkItem[], filename: string) {
  const headers = [
    'number',
    'title',
    'type',
    'priority',
    'status',
    'assignee',
    'slaState',
    'slaTarget',
    'service',
    'queue',
    'createdAt',
    'updatedAt',
  ];
  const rows = items.map((w) =>
    [
      w.number,
      w.title,
      w.type,
      w.priority,
      w.status,
      w.assignee?.name ?? '',
      w.slaState,
      w.slaTarget,
      w.service,
      w.queue ?? '',
      w.createdAt,
      w.updatedAt,
    ]
      .map((c) => csvEscape(String(c)))
      .join(','),
  );
  const blob = new Blob([[headers.join(','), ...rows].join('\n')], {
    type: 'text/csv;charset=utf-8',
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * Prefer store counts when any day in the window has real events.
 * Only fall back to light synthetic fill when the entire week is empty —
 * and mark those days so the UI can label them honestly.
 */
export function buildTrend(list: WorkItem[], allowSynthetic: boolean) {
  const days = last7DayKeys();
  const raw = days.map((day) => {
    const opened = list.filter(
      (w) => dayKey(new Date(w.createdAt)) === day.key,
    ).length;
    const closed = list.filter(
      (w) =>
        isResolvedStatus(w.status) && dayKey(new Date(w.updatedAt)) === day.key,
    ).length;
    return { ...day, opened, closed, synthetic: false };
  });
  const hasStoreActivity = raw.some((d) => d.opened > 0 || d.closed > 0);
  if (hasStoreActivity || !allowSynthetic) {
    return { trend: raw, usedSynthetic: false };
  }
  // Empty store week — optional visual fill, clearly labeled
  const filled = raw.map((day, idx) => {
    const seed = (day.date.getDate() + idx * 3) % 5;
    return {
      ...day,
      opened: seed === 0 ? 0 : seed,
      closed: Math.max(0, seed - 1),
      synthetic: true,
    };
  });
  return { trend: filled, usedSynthetic: true };
}

/**
 * SLA compliance for last 7 days on the **filtered** work-item set (S15).
 * 1) History from store activities for those ids only (not global seed dump)
 * 2) Else current slaState snapshot on the same list
 */
function computeSlaCompliance(list: WorkItem[], includeMockHistory: boolean): {
  pct: number | null;
  met: number;
  breached: number;
  source: 'history' | 'snapshot';
} {
  const weekAgo = startOfLocalDay(new Date());
  weekAgo.setDate(weekAgo.getDate() - 6);
  const weekStartMs = weekAgo.getTime();

  let histMet = 0;
  let histBreached = 0;
  if (includeMockHistory) {
    for (const w of list) {
      for (const a of getActivities(w.id)) {
        if (a.kind !== 'sla') continue;
        const at = new Date(a.at).getTime();
        if (Number.isNaN(at) || at < weekStartMs) continue;
        if (a.text === 'sla_breached') histBreached += 1;
        else if (a.text === 'sla_met' || a.text === 'sla_restored') histMet += 1;
      }
    }
  }
  if (histMet + histBreached > 0) {
    return {
      pct: Math.round((histMet / (histMet + histBreached)) * 100),
      met: histMet,
      breached: histBreached,
      source: 'history',
    };
  }

  const met = list.filter((w) => w.slaState === 'met').length;
  const breached = list.filter((w) => w.slaState === 'breached').length;
  const denom = met + breached;
  if (denom === 0) {
    const onTrack = list.filter(
      (w) => w.slaState === 'on_track' || w.slaState === 'met',
    ).length;
    const total = list.length;
    if (total === 0) {
      return { pct: null, met: 0, breached: 0, source: 'snapshot' };
    }
    return {
      pct: Math.round((onTrack / total) * 100),
      met: onTrack,
      breached: list.filter((w) => w.slaState === 'breached').length,
      source: 'snapshot',
    };
  }
  return {
    pct: Math.round((met / denom) * 100),
    met,
    breached,
    source: 'snapshot',
  };
}

/**
 * CSAT proxy from **filtered** resolved items (S14) — not global dashboard seed.
 * Share of resolved items that did not breach SLA (mock stand-in for surveys).
 */
function computeFilteredCsat(list: WorkItem[]): number | null {
  const resolved = list.filter((w) => isResolvedStatus(w.status));
  if (resolved.length === 0) return null;
  const happy = resolved.filter((w) => w.slaState !== 'breached').length;
  return Math.round((happy / resolved.length) * 100);
}

export function ReportsPage() {
  const t = useT();
  const { locale } = useI18n();
  const liveMode = !isMockMode();
  const metrics = useAsync(() => fetchDashboardMetrics(), []);
  const items = useAsync(() => fetchWorkItems(), []);
  const workload = useAsync(() => fetchWorkloadReport(), []);
  useWorkItemsSync(metrics.reload, items.reload, workload.reload);

  const [typeFilter, setTypeFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('');

  const filtered = useMemo(() => {
    const list = items.data ?? [];
    return list.filter((w) => {
      if (typeFilter && w.type !== typeFilter) return false;
      if (priorityFilter && w.priority !== priorityFilter) return false;
      return true;
    });
  }, [items.data, typeFilter, priorityFilter]);

  const derived = useMemo(() => {
    const list = filtered;
    const resolved = list.filter((w) => isResolvedStatus(w.status));
    const active = list.filter((w) => isActiveStatus(w.status));
    const breached = list.filter((w) => w.slaState === 'breached').length;
    const atRisk = list.filter((w) => w.slaState === 'at_risk').length;
    const unassigned = list.filter((w) => !w.assignee).length;
    const byPriority = Object.fromEntries(
      PRIORITIES.map((p) => [p, list.filter((w) => w.priority === p).length]),
    ) as Record<Priority, number>;
    const byStatus = Object.fromEntries(
      STATUSES.map((s) => [s, list.filter((w) => w.status === s).length]),
    ) as Record<WorkItemStatus, number>;
    const byType = {
      incident: list.filter((w) => w.type === 'incident').length,
      request: list.filter((w) => w.type === 'request').length,
      change: list.filter((w) => w.type === 'change').length,
      problem: list.filter((w) => w.type === 'problem').length,
    };
    const maxPriority = Math.max(1, ...Object.values(byPriority));
    const maxStatus = Math.max(1, ...Object.values(byStatus));

    const denom = resolved.length + active.length;
    const resolutionRate =
      denom === 0 ? 0 : Math.round((resolved.length / denom) * 100);

    // MTTR mock hours from resolved items (updatedAt − createdAt)
    let mttrHours = 0;
    if (resolved.length > 0) {
      const totalMs = resolved.reduce((sum, w) => {
        const a = new Date(w.createdAt).getTime();
        const b = new Date(w.updatedAt).getTime();
        return sum + Math.max(0, b - a);
      }, 0);
      mttrHours = Math.round((totalMs / resolved.length / 3_600_000) * 10) / 10;
    }

    const { trend, usedSynthetic } = buildTrend(list, !liveMode);
    const trendMax = Math.max(
      1,
      ...trend.map((d) => Math.max(d.opened, d.closed)),
    );

    // Top 5 assignees by open+closed load on filtered set
    const loadMap = new Map<string, { name: string; count: number }>();
    for (const w of list) {
      if (!w.assignee) continue;
      const cur = loadMap.get(w.assignee.id);
      if (cur) cur.count += 1;
      else loadMap.set(w.assignee.id, { name: w.assignee.name, count: 1 });
    }
    const topAssignees = [...loadMap.values()]
      .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name))
      .slice(0, 5);
    const maxAssignee = Math.max(1, ...topAssignees.map((a) => a.count), 1);

    const slaCompliance = computeSlaCompliance(list, !liveMode);
    const filteredCsat = liveMode
      ? (metrics.data?.satisfaction ?? null)
      : computeFilteredCsat(list);

    const fromList = {
      resolved: resolved.length,
      active: active.length,
      breached,
      atRisk,
      unassigned,
      total: list.length,
      byPriority,
      byStatus,
      byType,
      maxPriority,
      maxStatus,
      resolutionRate,
      mttrHours,
      trend,
      trendMax,
      usedSynthetic,
      topAssignees,
      maxAssignee,
      slaCompliance,
      filteredCsat,
      topUrgent: list
        .filter(
          (w) =>
            w.slaState === 'breached' ||
            w.slaState === 'at_risk' ||
            w.priority === 'critical',
        )
        .slice(0, 5),
    };
    if (liveMode && !typeFilter && !priorityFilter && workload.data) {
      return { ...fromList, ...overlayLiveSnapshot(fromList, workload.data) };
    }
    return fromList;
  }, [filtered, liveMode, metrics.data?.satisfaction, typeFilter, priorityFilter, workload.data]);

  const barPct = (n: number, max: number) =>
    `${Math.round((n / max) * 100)}%`;

  const exportCsv = () => {
    const stamp = new Date().toISOString().slice(0, 10);
    downloadWorkItemsCsv(filtered, `itsm-work-items-${stamp}.csv`);
  };

  const printReport = () => {
    window.print();
  };

  return (
    <section className="page page--reports">
      <div className="page-head reports-print-hide">
        <div>
          <p className="eyebrow">{t('reports.eyebrow')}</p>
          <h1>{t('reports.title')}</h1>
          <p className="page-subtitle">{t('reports.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">
            {liveMode ? t('reports.liveChip') : t('reports.derivedChip')}
          </span>
          <Button
            variant="secondary"
            size="sm"
            icon={<Printer size={16} />}
            onClick={printReport}
          >
            {t('reports.print')}
          </Button>
          <Button
            variant="secondary"
            size="sm"
            icon={<Download size={16} />}
            onClick={exportCsv}
            disabled={items.loading || filtered.length === 0}
          >
            {t('reports.exportCsv')}
          </Button>
        </div>
      </div>

      <div className="reports-print-title" aria-hidden>
        <h1>{t('reports.title')}</h1>
        <p>
          {new Date().toLocaleString(locale)} · {t('reports.filterCount', { n: derived.total })}
        </p>
      </div>

      <div className="filters-bar filters-bar--module reports-filters reports-print-hide">
        <Select
          label={t('reports.filterType')}
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            ...TYPES.map((ty) => ({
              value: ty,
              label: t(`workItemType.${ty}`),
            })),
          ]}
        />
        <Select
          label={t('reports.filterPriority')}
          value={priorityFilter}
          onChange={(e) => setPriorityFilter(e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            ...PRIORITIES.map((p) => ({
              value: p,
              label: t(`priority.${p}`),
            })),
          ]}
        />
        {(typeFilter || priorityFilter) && (
          <button
            type="button"
            className="text-button reports-filters__reset"
            onClick={() => {
              setTypeFilter('');
              setPriorityFilter('');
            }}
          >
            {t('app.reset')}
          </button>
        )}
        <span className="chip reports-filters__count">
          {t('reports.filterCount', { n: derived.total })}
        </span>
      </div>

      <div className="metrics-grid">
        {metrics.loading || items.loading ? (
          Array.from({ length: 4 }).map((_, i) => (
            <div className="metric-card metric-card--skeleton" key={i}>
              <Skeleton width={36} height={36} radius={10} />
              <div style={{ flex: 1 }}>
                <Skeleton width="40%" height={11} />
                <Skeleton width="30%" height={20} />
                <Skeleton width="55%" height={11} />
              </div>
            </div>
          ))
        ) : metrics.error && !metrics.data && items.error ? (
          <div className="panel" style={{ gridColumn: '1 / -1' }}>
            <ErrorState
              onRetry={() => {
                metrics.reload();
                items.reload();
              }}
            />
          </div>
        ) : (
          <>
            <MetricCard
              icon={<TicketCheck size={18} />}
              color="violet"
              value={String(derived.active)}
              label={t('reports.active')}
              detail={t('reports.activeDetail', { n: derived.total })}
            />
            <MetricCard
              icon={<Clock3 size={18} />}
              color="mint"
              value={String(derived.resolved)}
              label={t('reports.resolved')}
              detail={t('reports.resolvedDetail')}
            />
            <MetricCard
              icon={<ShieldAlert size={18} />}
              color="rose"
              value={String(derived.breached)}
              label={t('reports.breached')}
              detail={t('reports.breachedDetail', { n: derived.atRisk })}
            />
            <MetricCard
              icon={<Gauge size={18} />}
              color="amber"
              value={
                derived.filteredCsat != null
                  ? `${derived.filteredCsat}%`
                  : '—'
              }
              label={t('reports.satisfaction')}
              detail={
                derived.filteredCsat == null
                  ? t('reports.satisfactionEmpty')
                  : t(
                      liveMode
                        ? 'reports.satisfactionDetail'
                        : 'reports.satisfactionFilteredDetail',
                    )
              }
            />
          </>
        )}
      </div>

      {liveMode && workload.data?.change && workload.data.problem && workload.data.cmdb && workload.data.assets && (
        <div className="metrics-grid" aria-label={t('reports.modulesTitle')}>
          <MetricCard
            icon={<ShieldCheck size={18} />}
            color="mint"
            value={
              workload.data.change.successRate != null
                ? `${workload.data.change.successRate}%`
                : '—'
            }
            label={t('reports.changeSuccess')}
            detail={t('reports.changeSuccessDetail', {
              closed: workload.data.change.closed,
              rejected: workload.data.change.rejected,
            })}
          />
          <MetricCard
            icon={<Bug size={18} />}
            color="amber"
            value={String(workload.data.problem.open)}
            label={t('reports.problemsOpen')}
            detail={t('reports.problemsOpenDetail', {
              known: workload.data.problem.knownErrors,
            })}
          />
          <MetricCard
            icon={<Share2 size={18} />}
            color="violet"
            value={String(workload.data.cmdb.configurationItems)}
            label={t('reports.cmdb')}
            detail={t('reports.cmdbDetail', {
              orphans: workload.data.cmdb.orphans,
            })}
          />
          <MetricCard
            icon={<Package size={18} />}
            color="mint"
            value={String(workload.data.assets.total)}
            label={t('reports.assets')}
            detail={t('reports.assetsDetail', {
              inUse: workload.data.assets.inUse,
              inStock: workload.data.assets.inStock,
            })}
          />
          {workload.data.releases && (
            <MetricCard
              icon={<Rocket size={18} />}
              color="mint"
              value={
                workload.data.releases.successRate != null
                  ? `${workload.data.releases.successRate}%`
                  : '—'
              }
              label={t('reports.releaseSuccess')}
              detail={t('reports.releaseSuccessDetail', {
                inFlight: workload.data.releases.inFlight,
                rolledBack: workload.data.releases.rolledBack,
              })}
            />
          )}
          {workload.data.effort && (
            <MetricCard
              icon={<Clock3 size={18} />}
              color="violet"
              value={formatMinutes(workload.data.effort.totalMinutes)}
              label={t('reports.effortLogged')}
              detail={t('reports.effortLoggedDetail', {
                billable: formatMinutes(workload.data.effort.billableMinutes),
                items: workload.data.effort.itemsWithEffort,
              })}
            />
          )}
        </div>
      )}

      <div className="dashboard-grid">
        <section className="panel">
          <div className="panel__header panel__header--dense">
            <div>
              <h2>{t('reports.trendTitle')}</h2>
              <p>
                {derived.usedSynthetic
                  ? t('reports.trendHintSynthetic')
                  : t('reports.trendHintStore')}
              </p>
            </div>
            <TrendingUp size={18} aria-hidden className="muted" />
          </div>
          {derived.usedSynthetic && (
            <p className="reports-trend__synthetic-banner" role="note">
              {t('reports.trendSyntheticBanner')}
            </p>
          )}
          <div className="reports-trend" role="img" aria-label={t('reports.trendTitle')}>
            <div className="reports-trend__legend">
              <span className="reports-trend__swatch reports-trend__swatch--open" />
              {t('reports.trendOpened')}
              <span className="reports-trend__swatch reports-trend__swatch--resolved" />
              {t('reports.trendResolved')}
              {derived.usedSynthetic && (
                <span className="reports-trend__synthetic-tag">
                  {t('reports.trendSyntheticTag')}
                </span>
              )}
            </div>
            <ul className="reports-trend__bars">
              {derived.trend.map((d) => (
                <li key={d.key} className={d.synthetic ? 'is-synthetic' : undefined}>
                  <div className="reports-trend__pair" aria-hidden>
                    <div
                      className="reports-trend__col reports-trend__col--open"
                      style={{
                        height: `${Math.max(
                          d.opened > 0 || d.closed > 0 ? 4 : 0,
                          Math.round((d.opened / derived.trendMax) * 100),
                        )}%`,
                      }}
                      title={`${d.opened}`}
                    />
                    <div
                      className="reports-trend__col reports-trend__col--resolved"
                      style={{
                        height: `${Math.max(
                          d.opened > 0 || d.closed > 0 ? 4 : 0,
                          Math.round((d.closed / derived.trendMax) * 100),
                        )}%`,
                      }}
                      title={`${d.closed}`}
                    />
                  </div>
                  <span className="reports-trend__day">
                    {d.date.toLocaleDateString(locale, {
                      weekday: 'short',
                      day: 'numeric',
                    })}
                  </span>
                  <span className="reports-trend__counts muted">
                    {d.opened}/{d.closed}
                    {d.synthetic ? '*' : ''}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <aside className="right-rail">
          <section className="panel">
            <div className="panel__header panel__header--dense">
              <div>
                <h2>{t('reports.byType')}</h2>
                <p>{t('reports.byTypeHint')}</p>
              </div>
              <Layers size={18} aria-hidden className="muted" />
            </div>
            <ul className="reports-type-list">
              {(
                [
                  ['incident', derived.byType.incident],
                  ['request', derived.byType.request],
                  ['change', derived.byType.change],
                  ['problem', derived.byType.problem],
                ] as const
              ).map(([key, n]) => (
                <li key={key}>
                  <span>{t(`workItemType.${key}`)}</span>
                  <b>{n}</b>
                </li>
              ))}
            </ul>
          </section>
        </aside>
      </div>

      <div className="dashboard-grid">
        <section className="panel">
          <div className="panel__header panel__header--dense">
            <div>
              <h2>{t('reports.slaSummary')}</h2>
              <p>{t('reports.slaSummaryHint')}</p>
            </div>
          </div>
          <div className="reports-sla-grid">
            <Link
              to="/queues?tab=breached"
              className="reports-sla-card reports-sla-card--breach"
            >
              <ShieldAlert size={18} aria-hidden />
              <div>
                <b>{derived.breached}</b>
                <span>{t('sla.breached')}</span>
              </div>
              <ArrowRight size={16} aria-hidden />
            </Link>
            <Link
              to="/queues?sla=at_risk"
              className="reports-sla-card reports-sla-card--risk"
            >
              <AlertTriangle size={18} aria-hidden />
              <div>
                <b>{derived.atRisk}</b>
                <span>{t('sla.at_risk')}</span>
              </div>
              <ArrowRight size={16} aria-hidden />
            </Link>
            <Link to="/queues?tab=unassigned" className="reports-sla-card">
              <TicketCheck size={18} aria-hidden />
              <div>
                <b>{derived.unassigned}</b>
                <span>{t('queues.tabUnassigned')}</span>
              </div>
              <ArrowRight size={16} aria-hidden />
            </Link>
          </div>
          <div className="reports-kpi-row">
            <div className="reports-kpi">
              <Percent size={16} aria-hidden />
              <div>
                <b>{derived.resolutionRate}%</b>
                <span>{t('reports.resolutionRate')}</span>
                <small className="muted">{t('reports.resolutionRateHint')}</small>
              </div>
            </div>
            <div className="reports-kpi">
              <Timer size={16} aria-hidden />
              <div>
                <b>
                  {derived.resolved > 0
                    ? t('reports.mttrValue', { n: derived.mttrHours })
                    : '—'}
                </b>
                <span>{t('reports.mttr')}</span>
                <small className="muted">{t('reports.mttrHint')}</small>
              </div>
            </div>
            <div className="reports-kpi reports-kpi--sla">
              <ShieldCheck size={16} aria-hidden />
              <div>
                <b>
                  {derived.slaCompliance.pct != null
                    ? `${derived.slaCompliance.pct}%`
                    : '—'}
                </b>
                <span>{t('reports.slaCompliance')}</span>
                <small className="muted">
                  {derived.slaCompliance.source === 'history'
                    ? t('reports.slaComplianceHistory', {
                        met: derived.slaCompliance.met,
                        breached: derived.slaCompliance.breached,
                      })
                    : t('reports.slaComplianceSnapshot', {
                        met: derived.slaCompliance.met,
                        breached: derived.slaCompliance.breached,
                      })}
                </small>
              </div>
            </div>
          </div>
        </section>

        <aside className="right-rail">
          <section className="panel">
            <div className="panel__header panel__header--dense">
              <div>
                <h2>{t('reports.quickLinks')}</h2>
                <p>{t('reports.quickLinksHint')}</p>
              </div>
            </div>
            <ul className="reports-links">
              <li>
                <Link to="/queues">{t('nav.queues')}</Link>
              </li>
              <li>
                <Link to="/my-work">{t('nav.myWork')}</Link>
              </li>
              <li>
                <Link to="/">{t('nav.overview')}</Link>
              </li>
              <li>
                <Link to="/changes">{t('nav.changes')}</Link>
              </li>
            </ul>
          </section>
        </aside>
      </div>

      <div className="dashboard-grid reports-breakdown-grid">
        <section className="panel">
          <div className="panel__header panel__header--dense">
            <div>
              <h2>{t('reports.byPriority')}</h2>
              <p>{t('reports.byPriorityHint')}</p>
            </div>
          </div>
          <ul className="reports-bars" aria-label={t('reports.byPriority')}>
            {PRIORITIES.map((p) => (
              <li key={p}>
                <PriorityBadge priority={p} />
                <div className="reports-bar-track" aria-hidden>
                  <div
                    className={`reports-bar-fill reports-bar-fill--${p}`}
                    style={{
                      width: barPct(derived.byPriority[p], derived.maxPriority),
                    }}
                  />
                </div>
                <b>{derived.byPriority[p]}</b>
              </li>
            ))}
          </ul>
        </section>

        <section className="panel">
          <div className="panel__header panel__header--dense">
            <div>
              <h2>{t('reports.byStatus')}</h2>
              <p>{t('reports.byStatusHint')}</p>
            </div>
            <Activity size={18} aria-hidden className="muted" />
          </div>
          <ul className="reports-bars" aria-label={t('reports.byStatus')}>
            {STATUSES.map((s) => (
              <li key={s}>
                <StatusChip status={s} />
                <div className="reports-bar-track" aria-hidden>
                  <div
                    className="reports-bar-fill reports-bar-fill--status"
                    style={{
                      width: barPct(derived.byStatus[s], derived.maxStatus),
                    }}
                  />
                </div>
                <b>{derived.byStatus[s]}</b>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <div className="dashboard-grid reports-breakdown-grid">
        <section className="panel">
          <div className="panel__header panel__header--dense">
            <div>
              <h2>{t('reports.byAssignee')}</h2>
              <p>{t('reports.byAssigneeHint')}</p>
            </div>
            <Users size={18} aria-hidden className="muted" />
          </div>
          {derived.topAssignees.length === 0 ? (
            <p className="muted reports-empty">{t('reports.noAssignees')}</p>
          ) : (
            <ul className="reports-bars" aria-label={t('reports.byAssignee')}>
              {derived.topAssignees.map((a) => (
                <li key={a.name}>
                  <span className="reports-assignee-name" title={a.name}>
                    {a.name}
                  </span>
                  <div className="reports-bar-track" aria-hidden>
                    <div
                      className="reports-bar-fill reports-bar-fill--assignee"
                      style={{ width: barPct(a.count, derived.maxAssignee) }}
                    />
                  </div>
                  <b>{a.count}</b>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="panel reports-urgent-panel">
        <div className="panel__header panel__header--dense">
          <div>
            <h2>{t('reports.urgentQueue')}</h2>
            <p>{t('reports.urgentQueueHint')}</p>
          </div>
          <Link to="/queues?tab=breached" className="text-button reports-print-hide">
            {t('app.viewAll')} <span aria-hidden>→</span>
          </Link>
        </div>
        {derived.topUrgent.length === 0 ? (
          <p className="muted reports-empty">{t('reports.noUrgent')}</p>
        ) : (
          <ul className="reports-urgent-list">
            {derived.topUrgent.map((w) => (
              <li key={w.id}>
                <Link to={`/work-items/${w.id}`} className="reports-urgent-row">
                  <span className="reports-urgent-num">{w.number}</span>
                  <span className="reports-urgent-title">{w.title}</span>
                  <PriorityBadge priority={w.priority} />
                  <StatusChip status={w.status} />
                  <span className={`reports-urgent-sla is-${w.slaState}`}>
                    {w.slaTarget}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </section>
  );
}
