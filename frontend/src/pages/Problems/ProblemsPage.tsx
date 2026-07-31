import { useEffect, useMemo, useRef, useState } from 'react';
import { Plus, Search, X } from 'lucide-react';
import { useT } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useDensity } from '@/hooks/useDensity';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { useToast } from '@/hooks/useToast';
import { fetchProblems } from '@/api';
import {
  Button,
  EmptyState,
  ErrorState,
  Select,
  SkeletonRows,
  Avatar,
} from '@/components/ui';
import { PriorityBadge, StatusChip } from '@/components/data-display';
import { formatRelative } from '@/lib/format';
import type { Priority, Problem, WorkItemStatus } from '@/types';

export function ProblemsPage() {
  const t = useT();
  const { isCompact, toggleDensity } = useDensity();
  const { info } = useToast();
  const { data, loading, error, reload } = useAsync(() => fetchProblems(), []);
  const [query, setQuery] = useState('');
  const [priority, setPriority] = useState('');
  const [status, setStatus] = useState('');
  const [selected, setSelected] = useState<Problem | null>(null);

  const list = useMemo(() => {
    return (data ?? []).filter((p) => {
      if (priority && p.priority !== (priority as Priority)) return false;
      if (status && p.status !== (status as WorkItemStatus)) return false;
      if (!query.trim()) return true;
      const q = query.toLowerCase();
      return (
        p.number.toLowerCase().includes(q) ||
        p.title.toLowerCase().includes(q)
      );
    });
  }, [data, query, priority, status]);

  if (error && !loading && !data) {
    return (
      <section className="page">
        <div className="page-head">
          <div>
            <h1>{t('problems.title')}</h1>
            <p className="page-subtitle">{t('problems.subtitle')}</p>
          </div>
        </div>
        <ErrorState onRetry={reload} />
      </section>
    );
  }

  return (
    <section className="page">
      <div className="page-head">
        <div>
          <h1>{t('problems.title')}</h1>
          <p className="page-subtitle">{t('problems.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">{list.length}</span>
          <button
            type="button"
            className={`chip chip--toggle${isCompact ? ' is-on' : ''}`}
            onClick={toggleDensity}
          >
            {isCompact ? t('app.densityCompact') : t('app.densityComfortable')}
          </button>
          <Button
            variant="primary"
            icon={<Plus size={18} />}
            onClick={() => info(t('problems.createMock'))}
          >
            {t('problems.create')}
          </Button>
        </div>
      </div>

      <div className="filters-bar filters-bar--module">
        <label className="field">
          <span className="field__label">{t('app.search')}</span>
          <div className="field__control">
            <Search size={16} aria-hidden />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t('app.search')}
              aria-label={t('app.search')}
            />
          </div>
        </label>
        <Select
          label={t('problems.colPriority')}
          value={priority}
          onChange={(e) => setPriority(e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'critical', label: t('priority.critical') },
            { value: 'high', label: t('priority.high') },
            { value: 'medium', label: t('priority.medium') },
            { value: 'low', label: t('priority.low') },
          ]}
        />
        <Select
          label={t('problems.colStatus')}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'new', label: t('status.new') },
            { value: 'in_progress', label: t('status.in_progress') },
            { value: 'waiting', label: t('status.waiting') },
            { value: 'resolved', label: t('status.resolved') },
          ]}
        />
      </div>

      <div className="panel panel--flush data-table-wrap">
        <table className="data-table data-table--clickable">
          <thead>
            <tr>
              <th scope="col">{t('problems.colNumber')}</th>
              <th scope="col">{t('problems.colTitle')}</th>
              <th scope="col">{t('problems.colStatus')}</th>
              <th scope="col">{t('problems.colPriority')}</th>
              <th scope="col">{t('problems.colKnownError')}</th>
              <th scope="col">{t('problems.colIncidents')}</th>
              <th scope="col">{t('problems.colAssignee')}</th>
              <th scope="col">{t('problems.colUpdated')}</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={8}>
                  <SkeletonRows rows={3} />
                </td>
              </tr>
            ) : list.length === 0 ? (
              <tr>
                <td colSpan={8}>
                  <EmptyState
                    title={t('problems.emptyTitle')}
                    description={t('problems.emptyHint')}
                    actionLabel={t('app.reset')}
                    onAction={() => {
                      setQuery('');
                      setPriority('');
                      setStatus('');
                    }}
                  />
                </td>
              </tr>
            ) : (
              list.map((p) => (
                <tr
                  key={p.id}
                  tabIndex={0}
                  onClick={() => setSelected(p)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      setSelected(p);
                    }
                  }}
                >
                  <td>
                    <b className="mono accent">{p.number}</b>
                  </td>
                  <td>{p.title}</td>
                  <td>
                    <StatusChip status={p.status} />
                  </td>
                  <td>
                    <PriorityBadge priority={p.priority} />
                  </td>
                  <td>
                    {p.knownError
                      ? t('problems.knownErrorYes')
                      : t('problems.knownErrorNo')}
                  </td>
                  <td>{p.relatedIncidents}</td>
                  <td>
                    {p.assignee ? (
                      <span className="inline-person">
                        <Avatar initials={p.assignee.initials} size="sm" />
                        {p.assignee.name}
                      </span>
                    ) : (
                      <span className="muted">{t('overview.unassigned')}</span>
                    )}
                  </td>
                  <td className="muted">{formatRelative(p.updatedAt, t)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {selected && (
        <div
          className="drawer-backdrop"
          role="presentation"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setSelected(null);
          }}
        >
          <ProblemDrawer problem={selected} onClose={() => setSelected(null)} />
        </div>
      )}
    </section>
  );
}

function ProblemDrawer({
  problem,
  onClose,
}: {
  problem: Problem;
  onClose: () => void;
}) {
  const t = useT();
  const ref = useRef<HTMLElement>(null);
  useFocusTrap(ref, true);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [onClose]);

  return (
    <aside
      ref={ref}
      className="service-drawer module-detail-drawer"
      role="dialog"
      aria-modal="true"
      aria-labelledby="problem-detail-title"
    >
      <div className="service-drawer__head">
        <p className="eyebrow mono accent">{problem.number}</p>
        <button
          type="button"
          className="icon-btn"
          aria-label={t('app.close')}
          onClick={onClose}
        >
          <X size={18} />
        </button>
      </div>
      <h2 id="problem-detail-title">{problem.title}</h2>
      <div className="module-detail-chips">
        <StatusChip status={problem.status} />
        <PriorityBadge priority={problem.priority} />
        {problem.knownError && (
          <span className="chip chip--warn">{t('problems.knownErrorYes')}</span>
        )}
      </div>
      <dl className="module-detail-dl">
        <div>
          <dt>{t('problems.colIncidents')}</dt>
          <dd>{problem.relatedIncidents}</dd>
        </div>
        <div>
          <dt>{t('problems.colAssignee')}</dt>
          <dd>
            {problem.assignee ? (
              <span className="inline-person">
                <Avatar initials={problem.assignee.initials} size="sm" />
                {problem.assignee.name}
              </span>
            ) : (
              t('overview.unassigned')
            )}
          </dd>
        </div>
        {problem.service && (
          <div>
            <dt>{t('workItem.service')}</dt>
            <dd>{problem.service}</dd>
          </div>
        )}
        {problem.description && (
          <div className="module-detail-dl__wide">
            <dt>{t('workItem.description')}</dt>
            <dd>{problem.description}</dd>
          </div>
        )}
        {problem.rootCause && (
          <div className="module-detail-dl__wide">
            <dt>{t('problems.rootCause')}</dt>
            <dd>{problem.rootCause}</dd>
          </div>
        )}
        {problem.workaround && (
          <div className="module-detail-dl__wide">
            <dt>{t('problems.workaround')}</dt>
            <dd>{problem.workaround}</dd>
          </div>
        )}
      </dl>
      <div className="service-drawer__actions">
        <Button variant="secondary" fullWidth onClick={onClose}>
          {t('app.close')}
        </Button>
      </div>
    </aside>
  );
}
