import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useT } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useWorkItemsSync } from '@/hooks/useWorkItemsSync';
import { useDensity } from '@/hooks/useDensity';
import { fetchWorkItems } from '@/api';
import { currentUser } from '@/mock/data';
import { ErrorState, Tabs } from '@/components/ui';
import { OperatorGrid } from '@/components/data-display';

type Filter = 'all' | 'active' | 'waiting' | 'resolved';

export function MyWorkPage() {
  const t = useT();
  const navigate = useNavigate();
  const { isCompact, toggleDensity } = useDensity();
  const [filter, setFilter] = useState<Filter>('all');
  const { data, loading, error, reload } = useAsync(
    () => fetchWorkItems({ assigneeId: currentUser.id }),
    [],
  );
  useWorkItemsSync(reload);

  const mine = useMemo(() => {
    const list = data ?? [];
    return list.filter((w) => {
      if (filter === 'active')
        return w.status === 'new' || w.status === 'in_progress';
      if (filter === 'waiting') return w.status === 'waiting';
      if (filter === 'resolved')
        return w.status === 'resolved' || w.status === 'closed';
      return true;
    });
  }, [data, filter]);

  if (error && !loading && !data) {
    return (
      <section className="page">
        <div className="page-head">
          <div>
            <h1>{t('myWork.title')}</h1>
            <p className="page-subtitle">{t('myWork.subtitle')}</p>
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
          <h1>{t('myWork.title')}</h1>
          <p className="page-subtitle">{t('myWork.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">{t('myWork.count', { n: mine.length })}</span>
          <button
            type="button"
            className={`chip chip--toggle${isCompact ? ' is-on' : ''}`}
            onClick={toggleDensity}
          >
            {isCompact ? t('app.densityCompact') : t('app.densityComfortable')}
          </button>
        </div>
      </div>

      <Tabs
        value={filter}
        onChange={(id) => setFilter(id as Filter)}
        items={[
          { id: 'all', label: t('myWork.filterAll') },
          { id: 'active', label: t('myWork.filterActive') },
          { id: 'waiting', label: t('myWork.filterWaiting') },
          { id: 'resolved', label: t('myWork.filterResolved') },
        ]}
      />

      <OperatorGrid
        items={mine}
        loading={loading}
        emptyTitle={t('myWork.emptyTitle')}
        emptyHint={t('myWork.emptyHint')}
        emptyActionLabel={t('myWork.emptyAction')}
        onEmptyAction={() => navigate('/queues')}
      />
    </section>
  );
}
