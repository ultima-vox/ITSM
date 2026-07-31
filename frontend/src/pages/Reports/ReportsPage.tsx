import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowRight,
  Clock3,
  Gauge,
  ShieldAlert,
  TicketCheck,
  Layers,
  Activity,
} from 'lucide-react';
import { useT } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useWorkItemsSync } from '@/hooks/useWorkItemsSync';
import { fetchDashboardMetrics, fetchWorkItems } from '@/api';
import type { Priority, WorkItemStatus } from '@/types';
import { ErrorState, Skeleton } from '@/components/ui';
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

export function ReportsPage() {
  const t = useT();
  const metrics = useAsync(() => fetchDashboardMetrics(), []);
  const items = useAsync(() => fetchWorkItems(), []);
  useWorkItemsSync(metrics.reload, items.reload);

  const derived = useMemo(() => {
    const list = items.data ?? [];
    const resolved = list.filter(
      (w) => w.status === 'resolved' || w.status === 'closed',
    ).length;
    const active = list.filter(
      (w) =>
        w.status === 'new' ||
        w.status === 'in_progress' ||
        w.status === 'waiting',
    ).length;
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
    return {
      resolved,
      active,
      breached,
      atRisk,
      unassigned,
      total: list.length,
      byPriority,
      byStatus,
      byType,
      maxPriority,
      maxStatus,
      topUrgent: list
        .filter(
          (w) =>
            w.slaState === 'breached' ||
            w.slaState === 'at_risk' ||
            w.priority === 'critical',
        )
        .slice(0, 5),
    };
  }, [items.data]);

  const barPct = (n: number, max: number) =>
    `${Math.round((n / max) * 100)}%`;

  return (
    <section className="page page--reports">
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('reports.eyebrow')}</p>
          <h1>{t('reports.title')}</h1>
          <p className="page-subtitle">{t('reports.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">{t('reports.liveChip')}</span>
        </div>
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
            <ErrorState onRetry={() => { metrics.reload(); items.reload(); }} />
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
                metrics.data ? `${metrics.data.satisfaction}%` : '—'
              }
              label={t('reports.satisfaction')}
              detail={t('reports.satisfactionDetail')}
            />
          </>
        )}
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
                    style={{ width: barPct(derived.byPriority[p], derived.maxPriority) }}
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
                    style={{ width: barPct(derived.byStatus[s], derived.maxStatus) }}
                  />
                </div>
                <b>{derived.byStatus[s]}</b>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className="panel reports-urgent-panel">
        <div className="panel__header panel__header--dense">
          <div>
            <h2>{t('reports.urgentQueue')}</h2>
            <p>{t('reports.urgentQueueHint')}</p>
          </div>
          <Link to="/queues?tab=breached" className="text-button">
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
                  <span
                    className={`reports-urgent-sla is-${w.slaState}`}
                  >
                    {w.slaTarget}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="dashboard-grid">
        <aside className="right-rail" style={{ gridColumn: '1 / -1' }}>
          <section className="panel">
            <div className="panel__header panel__header--dense">
              <div>
                <h2>{t('reports.quickLinks')}</h2>
                <p>{t('reports.quickLinksHint')}</p>
              </div>
            </div>
            <ul className="reports-links reports-links--row">
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
    </section>
  );
}
