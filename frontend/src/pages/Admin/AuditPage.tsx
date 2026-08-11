import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ScrollText, Filter } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import {
  fetchAuditActionKeys,
  fetchAuditEvents,
  listAuditActionKeys,
  isMockMode,
} from '@/api';
import { Badge, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { formatDateTime, formatRelative } from '@/lib/format';
import type { AuditEvent } from '@/types';

function actionLabel(t: (k: string) => string, action: string): string {
  const key = `audit.actions.${action}`;
  const translated = t(key);
  return translated === key ? action : translated;
}

function objectTypeLabel(t: (k: string) => string, objectType: string): string {
  const key = `audit.objectTypes.${objectType}`;
  const translated = t(key);
  return translated === key ? objectType : translated;
}

function actionTone(
  action: string,
): 'violet' | 'mint' | 'amber' | 'rose' | 'blue' | 'neutral' {
  switch (action) {
    case 'create':
      return 'mint';
    case 'delete':
      return 'rose';
    case 'escalate':
    case 'sla':
      return 'amber';
    case 'assign':
    case 'resolve':
      return 'blue';
    case 'login':
    case 'config':
      return 'neutral';
    default:
      return 'violet';
  }
}

function objectHref(ev: AuditEvent): string | null {
  const type = (ev.objectType || '').toLowerCase().replace(/_/g, '-');
  if (
    (type === 'work-item' || type === 'workitem' || type === 'incident') &&
    ev.objectId
  ) {
    return `/work-items/${ev.objectId}`;
  }
  if ((type === 'problem' || type === 'change') && ev.objectId) {
    return `/${type}s/${ev.objectId}`;
  }
  if ((type === 'knowledge' || type === 'knowledge-article') && ev.objectId) {
    return `/knowledge/${ev.objectId}`;
  }
  return null;
}

export function AuditPage() {
  const t = useT();
  const { locale } = useI18n();
  const liveMode = !isMockMode();
  const [action, setAction] = useState<string>('all');
  const [liveActionKeys, setLiveActionKeys] = useState<string[] | null>(null);

  useEffect(() => {
    if (!liveMode) {
      setLiveActionKeys(null);
      return;
    }
    let cancelled = false;
    void fetchAuditActionKeys()
      .then((keys) => {
        if (!cancelled) setLiveActionKeys(keys);
      })
      .catch(() => {
        if (!cancelled) setLiveActionKeys([]);
      });
    return () => {
      cancelled = true;
    };
  }, [liveMode]);

  const actionKeys = useMemo(() => {
    if (liveMode) return liveActionKeys ?? [];
    return listAuditActionKeys();
  }, [liveMode, liveActionKeys]);

  const { data, loading, error, reload } = useAsync(
    () => fetchAuditEvents({ action, limit: 100 }),
    [action],
  );

  const events = data ?? [];

  return (
    <section className="page page--audit">
      <div className="page-head">
        <div>
          <h1>{t('audit.title')}</h1>
          <p className="page-subtitle">{t('audit.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">
            <ScrollText size={14} aria-hidden />
            {t('audit.eventCount', { n: events.length })}
          </span>
          {!liveMode && (
            <span className="chip chip--muted">{t('audit.mockHint')}</span>
          )}
        </div>
      </div>

      <div
        className="filter-chips audit-page__chips"
        role="group"
        aria-label={t('audit.filterByAction')}
      >
        <button
          type="button"
          className={`chip chip--toggle${action === 'all' ? ' is-on' : ''}`}
          onClick={() => setAction('all')}
        >
          <Filter size={14} aria-hidden />
          {t('app.all')}
        </button>
        {actionKeys.map((key) => (
          <button
            key={key}
            type="button"
            className={`chip chip--toggle${action === key ? ' is-on' : ''}`}
            onClick={() => setAction(key)}
            aria-pressed={action === key}
          >
            {actionLabel(t, key)}
          </button>
        ))}
      </div>

      {error && <ErrorState onRetry={reload} />}

      {loading && !data && (
        <div className="panel audit-table-wrap" aria-busy="true">
          <Skeleton height={36} />
          <Skeleton height={36} className="mt-2" />
          <Skeleton height={36} className="mt-2" />
          <Skeleton height={36} className="mt-2" />
        </div>
      )}

      {!loading && !error && events.length === 0 && (
        <EmptyState
          title={t('audit.emptyTitle')}
          description={t('audit.emptyHint')}
          icon={<ScrollText size={22} />}
          actionLabel={action !== 'all' ? t('app.reset') : undefined}
          onAction={action !== 'all' ? () => setAction('all') : undefined}
        />
      )}

      {events.length > 0 && (
        <div className="data-table-wrap panel audit-table-wrap">
          <table className="data-table data-table--dense">
            <thead>
              <tr>
                <th scope="col">{t('audit.colTime')}</th>
                <th scope="col">{t('audit.colActor')}</th>
                <th scope="col">{t('audit.colAction')}</th>
                <th scope="col">{t('audit.colObject')}</th>
                <th scope="col">{t('audit.colDetail')}</th>
              </tr>
            </thead>
            <tbody>
              {events.map((ev) => {
                const href = objectHref(ev);
                return (
                  <tr key={ev.id}>
                    <td>
                      <time dateTime={ev.at} title={formatDateTime(ev.at, locale)}>
                        {formatRelative(ev.at, t)}
                      </time>
                    </td>
                    <td>
                      <span className="audit-actor">
                        <span className="audit-actor__avatar" aria-hidden>
                          {ev.actor.initials}
                        </span>
                        {ev.actor.name}
                      </span>
                    </td>
                    <td>
                      <Badge tone={actionTone(ev.action)}>
                        {actionLabel(t, ev.action)}
                      </Badge>
                    </td>
                    <td>
                      <div className="audit-object">
                        <span className="type-pill type-pill--sm">
                          {objectTypeLabel(t, ev.objectType)}
                        </span>
                        {href ? (
                          <Link to={href} className="text-link">
                            {ev.objectLabel ?? ev.objectId ?? '—'}
                          </Link>
                        ) : (
                          <span>{ev.objectLabel ?? ev.objectId ?? '—'}</span>
                        )}
                      </div>
                    </td>
                    <td className="muted">{ev.detail ?? '—'}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
