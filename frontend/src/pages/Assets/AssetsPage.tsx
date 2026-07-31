import { useEffect, useMemo, useRef, useState } from 'react';
import { Plus, Search, X } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useDensity } from '@/hooks/useDensity';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { useToast } from '@/hooks/useToast';
import { fetchAssets } from '@/api';
import {
  Button,
  EmptyState,
  ErrorState,
  Select,
  SkeletonRows,
} from '@/components/ui';
import { StatusChip } from '@/components/data-display';
import { formatDate } from '@/lib/format';
import type { Asset } from '@/types';

export function AssetsPage() {
  const t = useT();
  const { locale } = useI18n();
  const { isCompact, toggleDensity } = useDensity();
  const { info } = useToast();
  const { data, loading, error, reload } = useAsync(() => fetchAssets(), []);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [selected, setSelected] = useState<Asset | null>(null);

  const list = useMemo(() => {
    return (data ?? []).filter((a) => {
      if (status && a.status !== status) return false;
      if (!query.trim()) return true;
      const q = query.toLowerCase();
      return (
        a.tag.toLowerCase().includes(q) ||
        a.name.toLowerCase().includes(q) ||
        a.location.toLowerCase().includes(q) ||
        (a.assignedTo?.toLowerCase().includes(q) ?? false)
      );
    });
  }, [data, query, status]);

  if (error && !loading && !data) {
    return (
      <section className="page">
        <div className="page-head">
          <div>
            <h1>{t('assets.title')}</h1>
            <p className="page-subtitle">{t('assets.subtitle')}</p>
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
          <h1>{t('assets.title')}</h1>
          <p className="page-subtitle">{t('assets.subtitle')}</p>
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
            onClick={() => info(t('assets.addMock'))}
          >
            {t('assets.addAsset')}
          </Button>
        </div>
      </div>

      <div className="module-toolbar">
        <label className="field" style={{ flex: '1 1 220px', maxWidth: 360 }}>
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
          label={t('assets.colStatus')}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'in_use', label: t('status.in_use') },
            { value: 'stock', label: t('status.stock') },
            { value: 'repair', label: t('status.repair') },
            { value: 'retired', label: t('status.retired') },
          ]}
        />
      </div>

      <div className="panel panel--flush data-table-wrap">
        <table className="data-table data-table--clickable">
          <thead>
            <tr>
              <th scope="col">{t('assets.colTag')}</th>
              <th scope="col">{t('assets.colName')}</th>
              <th scope="col">{t('assets.colType')}</th>
              <th scope="col">{t('assets.colStatus')}</th>
              <th scope="col">{t('assets.colAssignee')}</th>
              <th scope="col">{t('assets.colLocation')}</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6}>
                  <SkeletonRows rows={4} />
                </td>
              </tr>
            ) : list.length === 0 ? (
              <tr>
                <td colSpan={6}>
                  <EmptyState
                    title={t('assets.emptyTitle')}
                    description={t('assets.emptyHint')}
                    actionLabel={t('app.reset')}
                    onAction={() => {
                      setQuery('');
                      setStatus('');
                    }}
                  />
                </td>
              </tr>
            ) : (
              list.map((a) => (
                <tr
                  key={a.id}
                  tabIndex={0}
                  onClick={() => setSelected(a)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      setSelected(a);
                    }
                  }}
                >
                  <td>
                    <b className="mono">{a.tag}</b>
                  </td>
                  <td>
                    {a.name}
                    <small className="cell-sub">
                      {formatDate(a.purchasedAt, locale)}
                    </small>
                  </td>
                  <td>{t(a.typeKey)}</td>
                  <td>
                    <StatusChip status={a.status} />
                  </td>
                  <td>{a.assignedTo ?? t('assets.unassigned')}</td>
                  <td>{a.location}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {selected && (
        <ModuleDetailDrawer
          title={selected.name}
          subtitle={selected.tag}
          onClose={() => setSelected(null)}
        >
          <dl className="module-detail-dl">
            <div>
              <dt>{t('assets.colStatus')}</dt>
              <dd>
                <StatusChip status={selected.status} />
              </dd>
            </div>
            <div>
              <dt>{t('assets.colType')}</dt>
              <dd>{t(selected.typeKey)}</dd>
            </div>
            <div>
              <dt>{t('assets.colAssignee')}</dt>
              <dd>{selected.assignedTo ?? t('assets.unassigned')}</dd>
            </div>
            <div>
              <dt>{t('assets.colLocation')}</dt>
              <dd>{selected.location}</dd>
            </div>
            <div>
              <dt>{t('assets.purchased')}</dt>
              <dd>{formatDate(selected.purchasedAt, locale)}</dd>
            </div>
            {selected.serial && (
              <div>
                <dt>{t('assets.serial')}</dt>
                <dd className="mono">{selected.serial}</dd>
              </div>
            )}
            {selected.model && (
              <div>
                <dt>{t('assets.model')}</dt>
                <dd>{selected.model}</dd>
              </div>
            )}
            {selected.vendor && (
              <div>
                <dt>{t('assets.vendor')}</dt>
                <dd>{selected.vendor}</dd>
              </div>
            )}
            {selected.costCenter && (
              <div>
                <dt>{t('assets.costCenter')}</dt>
                <dd>{selected.costCenter}</dd>
              </div>
            )}
            {selected.notes && (
              <div className="module-detail-dl__wide">
                <dt>{t('assets.notes')}</dt>
                <dd>{selected.notes}</dd>
              </div>
            )}
          </dl>
        </ModuleDetailDrawer>
      )}
    </section>
  );
}

function ModuleDetailDrawer({
  title,
  subtitle,
  onClose,
  children,
}: {
  title: string;
  subtitle: string;
  onClose: () => void;
  children: React.ReactNode;
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
    <div
      className="drawer-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <aside
        ref={ref}
        className="service-drawer module-detail-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="module-detail-title"
      >
        <div className="service-drawer__head">
          <p className="eyebrow mono">{subtitle}</p>
          <button
            type="button"
            className="icon-btn"
            aria-label={t('app.close')}
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </div>
        <h2 id="module-detail-title">{title}</h2>
        {children}
        <div className="service-drawer__actions">
          <Button variant="secondary" fullWidth onClick={onClose}>
            {t('app.close')}
          </Button>
        </div>
      </aside>
    </div>
  );
}
