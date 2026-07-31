import { useEffect, useMemo, useRef, useState } from 'react';
import { Plus, Search, X } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useDensity } from '@/hooks/useDensity';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { useToast } from '@/hooks/useToast';
import { fetchChanges } from '@/api';
import {
  Button,
  EmptyState,
  ErrorState,
  Select,
  SkeletonRows,
  Avatar,
} from '@/components/ui';
import { PriorityBadge, StatusChip } from '@/components/data-display';
import { formatDateTime } from '@/lib/format';
import type { Change } from '@/types';

export function ChangesPage() {
  const t = useT();
  const { locale } = useI18n();
  const { isCompact, toggleDensity } = useDensity();
  const { info } = useToast();
  const { data, loading, error, reload } = useAsync(() => fetchChanges(), []);
  const [query, setQuery] = useState('');
  const [type, setType] = useState('');
  const [status, setStatus] = useState('');
  const [selected, setSelected] = useState<Change | null>(null);

  const list = useMemo(() => {
    return (data ?? []).filter((c) => {
      if (type && c.type !== type) return false;
      if (status && c.status !== status) return false;
      if (!query.trim()) return true;
      const q = query.toLowerCase();
      return (
        c.number.toLowerCase().includes(q) ||
        c.title.toLowerCase().includes(q)
      );
    });
  }, [data, query, type, status]);

  if (error && !loading && !data) {
    return (
      <section className="page">
        <div className="page-head">
          <div>
            <h1>{t('changes.title')}</h1>
            <p className="page-subtitle">{t('changes.subtitle')}</p>
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
          <h1>{t('changes.title')}</h1>
          <p className="page-subtitle">{t('changes.subtitle')}</p>
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
            onClick={() => info(t('changes.createMock'))}
          >
            {t('changes.create')}
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
          label={t('changes.colType')}
          value={type}
          onChange={(e) => setType(e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'standard', label: t('changeType.standard') },
            { value: 'normal', label: t('changeType.normal') },
            { value: 'emergency', label: t('changeType.emergency') },
          ]}
        />
        <Select
          label={t('changes.colStatus')}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'draft', label: t('status.draft') },
            { value: 'scheduled', label: t('status.scheduled') },
            { value: 'in_progress', label: t('status.in_progress') },
            { value: 'cab_review', label: t('status.cab_review') },
            { value: 'completed', label: t('status.completed') },
          ]}
        />
      </div>

      <div className="panel panel--flush data-table-wrap">
        <table className="data-table data-table--clickable">
          <thead>
            <tr>
              <th scope="col">{t('changes.colNumber')}</th>
              <th scope="col">{t('changes.colTitle')}</th>
              <th scope="col">{t('changes.colType')}</th>
              <th scope="col">{t('changes.colStatus')}</th>
              <th scope="col">{t('changes.colRisk')}</th>
              <th scope="col">{t('changes.colWindow')}</th>
              <th scope="col">{t('changes.colAssignee')}</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7}>
                  <SkeletonRows rows={3} />
                </td>
              </tr>
            ) : list.length === 0 ? (
              <tr>
                <td colSpan={7}>
                  <EmptyState
                    title={t('changes.emptyTitle')}
                    description={t('changes.emptyHint')}
                    actionLabel={t('app.reset')}
                    onAction={() => {
                      setQuery('');
                      setType('');
                      setStatus('');
                    }}
                  />
                </td>
              </tr>
            ) : (
              list.map((c) => (
                <tr
                  key={c.id}
                  tabIndex={0}
                  onClick={() => setSelected(c)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      setSelected(c);
                    }
                  }}
                >
                  <td>
                    <b className="mono accent">{c.number}</b>
                  </td>
                  <td>{c.title}</td>
                  <td>{t(`changeType.${c.type}`)}</td>
                  <td>
                    <StatusChip status={c.status} />
                  </td>
                  <td>
                    <PriorityBadge priority={c.risk} />
                  </td>
                  <td className="window-cell">
                    {formatDateTime(c.plannedStart, locale)}
                    <span className="muted"> {t('changes.windowTo')} </span>
                    {formatDateTime(c.plannedEnd, locale)}
                  </td>
                  <td>
                    {c.assignee ? (
                      <span className="inline-person">
                        <Avatar initials={c.assignee.initials} size="sm" />
                        {c.assignee.name}
                      </span>
                    ) : (
                      <span className="muted">{t('overview.unassigned')}</span>
                    )}
                  </td>
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
          <ChangeDrawer change={selected} onClose={() => setSelected(null)} />
        </div>
      )}
    </section>
  );
}

function ChangeDrawer({
  change,
  onClose,
}: {
  change: Change;
  onClose: () => void;
}) {
  const t = useT();
  const { locale } = useI18n();
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
      aria-labelledby="change-detail-title"
    >
      <div className="service-drawer__head">
        <p className="eyebrow mono accent">{change.number}</p>
        <button
          type="button"
          className="icon-btn"
          aria-label={t('app.close')}
          onClick={onClose}
        >
          <X size={18} />
        </button>
      </div>
      <h2 id="change-detail-title">{change.title}</h2>
      <div className="module-detail-chips">
        <StatusChip status={change.status} />
        <span className="type-pill">{t(`changeType.${change.type}`)}</span>
        <PriorityBadge priority={change.risk} />
      </div>
      <dl className="module-detail-dl">
        <div>
          <dt>{t('changes.colWindow')}</dt>
          <dd>
            {formatDateTime(change.plannedStart, locale)}
            <br />
            → {formatDateTime(change.plannedEnd, locale)}
          </dd>
        </div>
        <div>
          <dt>{t('changes.colAssignee')}</dt>
          <dd>
            {change.assignee ? (
              <span className="inline-person">
                <Avatar initials={change.assignee.initials} size="sm" />
                {change.assignee.name}
              </span>
            ) : (
              t('overview.unassigned')
            )}
          </dd>
        </div>
        {change.service && (
          <div>
            <dt>{t('workItem.service')}</dt>
            <dd>{change.service}</dd>
          </div>
        )}
        {change.description && (
          <div className="module-detail-dl__wide">
            <dt>{t('workItem.description')}</dt>
            <dd>{change.description}</dd>
          </div>
        )}
        {change.implementationPlan && (
          <div className="module-detail-dl__wide">
            <dt>{t('changes.implementationPlan')}</dt>
            <dd>{change.implementationPlan}</dd>
          </div>
        )}
        {change.backoutPlan && (
          <div className="module-detail-dl__wide">
            <dt>{t('changes.backoutPlan')}</dt>
            <dd>{change.backoutPlan}</dd>
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
