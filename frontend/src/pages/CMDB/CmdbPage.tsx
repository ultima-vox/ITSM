import { useMemo, useState } from 'react';
import {
  ArrowRight,
  ArrowUpRight,
  Boxes,
  CheckCircle2,
  Cloud,
  MoreHorizontal,
  Network,
  Plus,
  Search,
  Server,
  Sparkles,
} from 'lucide-react';
import { useT } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { fetchConfigurationItems } from '@/api';
import { Button, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { StatusChip } from '@/components/data-display';

const icons = {
  server: Server,
  cloud: Cloud,
  network: Network,
  database: Boxes,
  app: Boxes,
} as const;

export function CmdbPage() {
  const t = useT();
  const [filter, setFilter] = useState('all');
  const [q, setQ] = useState('');
  const { data, loading, error, reload } = useAsync(
    () => fetchConfigurationItems(),
    [],
  );

  const list = useMemo(() => {
    let items = data ?? [];
    if (filter === 'services')
      items = items.filter((c) => c.icon === 'cloud' || c.icon === 'app');
    if (filter === 'infra')
      items = items.filter(
        (c) => c.icon === 'server' || c.icon === 'network' || c.icon === 'database',
      );
    if (filter === 'apps') items = items.filter((c) => c.icon === 'app');
    if (q.trim()) {
      const needle = q.toLowerCase();
      items = items.filter(
        (c) =>
          c.name.toLowerCase().includes(needle) ||
          c.owner.toLowerCase().includes(needle),
      );
    }
    return items;
  }, [data, filter, q]);

  if (error && !loading && !data) {
    return (
      <section className="page page--cmdb">
        <div className="page-head">
          <div>
            <p className="eyebrow">{t('cmdb.kicker')}</p>
            <h1>{t('cmdb.title')}</h1>
          </div>
        </div>
        <ErrorState onRetry={reload} />
      </section>
    );
  }

  return (
    <section className="page page--cmdb">
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('cmdb.kicker')}</p>
          <h1>{t('cmdb.title')}</h1>
          <p className="page-subtitle">{t('cmdb.subtitle')}</p>
        </div>
        <Button variant="primary" icon={<Plus size={18} />}>
          {t('cmdb.addCi')}
        </Button>
      </div>

      <div className="cmdb-stats">
        <div>
          <span>
            <Boxes size={15} />
          </span>
          <b>14 286</b>
          <small>{t('cmdb.statItems')}</small>
          <em>+2.4%</em>
        </div>
        <div>
          <span>
            <CheckCircle2 size={15} />
          </span>
          <b>97.8%</b>
          <small>{t('cmdb.statFreshness')}</small>
          <em>{t('cmdb.statHealthy')}</em>
        </div>
        <div>
          <span>
            <Network size={15} />
          </span>
          <b>36 104</b>
          <small>{t('cmdb.statRelations')}</small>
          <em>{t('cmdb.statGraph')}</em>
        </div>
      </div>

      <div className="cmdb-workspace">
        <section className="ci-panel">
          <div className="ci-head">
            <div>
              <h2>{t('cmdb.ciTitle')}</h2>
              <p>{t('cmdb.ciHint')}</p>
            </div>
            <label>
              <Search size={16} aria-hidden />
              <input
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder={t('cmdb.searchCi')}
                aria-label={t('cmdb.searchCi')}
              />
            </label>
          </div>
          <div className="ci-filters">
            {(
              [
                ['all', t('cmdb.filterAll'), 14286],
                ['services', t('cmdb.filterServices'), 124],
                ['infra', t('cmdb.filterInfra'), 9812],
                ['apps', t('cmdb.filterApps'), 1006],
              ] as const
            ).map(([id, label, count]) => (
              <button
                key={id}
                type="button"
                className={filter === id ? 'is-active' : undefined}
                onClick={() => setFilter(id)}
              >
                {label} <b>{count.toLocaleString()}</b>
              </button>
            ))}
          </div>

          {loading ? (
            <div style={{ padding: 16 }}>
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} height={48} radius={8} className="mb-2" />
              ))}
            </div>
          ) : list.length === 0 ? (
            <EmptyState
              title={t('cmdb.emptyTitle')}
              description={t('cmdb.emptyHint')}
            />
          ) : (
            <div className="ci-list">
              {list.map((ci) => {
                const Icon = icons[ci.icon] ?? Server;
                return (
                  <button type="button" className="ci-row" key={ci.id}>
                    <span className={`ci-icon ci-icon--${ci.tone}`}>
                      <Icon size={16} />
                    </span>
                    <span className="ci-main">
                      <b>{ci.name}</b>
                      <small>
                        {t(ci.kindKey)} · {ci.owner}
                      </small>
                    </span>
                    <StatusChip status={ci.status} />
                    <ArrowRight size={16} aria-hidden />
                  </button>
                );
              })}
            </div>
          )}
          <button type="button" className="ci-more">
            {t('cmdb.openCmdb')} <ArrowRight size={15} />
          </button>
        </section>

        <aside className="map-panel">
          <div className="map-head">
            <div>
              <h2>{t('cmdb.mapTitle')}</h2>
              <p>{t('cmdb.mapService')}</p>
            </div>
            <button type="button" className="icon-btn" aria-label={t('app.more')}>
              <MoreHorizontal size={18} />
            </button>
          </div>
          <div className="dependency-map" role="img" aria-label={t('cmdb.mapTitle')}>
            <span className="map-node map-node--service">
              <Cloud size={13} />
              {t('cmdb.mapNodeService')}
            </span>
            <span className="map-node map-node--api">
              <Server size={13} />
              {t('cmdb.mapNodeApi')}
            </span>
            <span className="map-node map-node--db">
              <Boxes size={13} />
              {t('cmdb.mapNodeDb')}
            </span>
            <span className="map-node map-node--net">
              <Network size={13} />
              {t('cmdb.mapNodeNet')}
            </span>
            <i className="map-link map-link--a" />
            <i className="map-link map-link--b" />
            <i className="map-link map-link--c" />
          </div>
          <div className="map-footer">
            <span>
              <i className="is-ok" aria-hidden />
              <span className="map-footer__label">{t('cmdb.healthy')}</span>
            </span>
            <span>
              <i className="is-warn" aria-hidden />
              <span className="map-footer__label">{t('cmdb.attention')}</span>
            </span>
            <button type="button" className="text-link">
              {t('cmdb.fullMap')} <ArrowUpRight size={13} />
            </button>
          </div>
        </aside>
      </div>

      <div className="impact-strip">
        <span className="impact-icon">
          <Sparkles size={18} />
        </span>
        <div>
          <b>{t('cmdb.impactReady')}</b>
          <p>{t('cmdb.impactText')}</p>
        </div>
        <button type="button">
          {t('cmdb.openImpact')} <ArrowRight size={15} />
        </button>
      </div>
    </section>
  );
}
