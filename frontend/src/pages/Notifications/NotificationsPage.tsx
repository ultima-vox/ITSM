import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Bell,
  Clock3,
  RefreshCw,
  ShieldAlert,
  UserPlus,
} from 'lucide-react';
import { useT } from '@/i18n';
import { formatRelative } from '@/lib/format';
import {
  fetchNotifications,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  subscribeNotifications,
  useMock,
  type AppNotification,
  type NotificationKind,
} from '@/api';
import {
  filterNotificationsByPrefs,
  loadNotificationPrefs,
  subscribeNotificationPrefs,
} from '@/lib/notificationPrefs';
import { Button, EmptyState, ErrorState, Skeleton } from '@/components/ui';

const kindIcon: Record<NotificationKind, typeof Bell> = {
  sla: ShieldAlert,
  breach: ShieldAlert,
  assign: UserPlus,
  mention: Bell,
};

function notifTitle(
  n: AppNotification,
  t: (k: string, v?: Record<string, string | number>) => string,
): string {
  if (n.title?.trim()) return n.title;
  return t(n.titleKey, n.titleVars);
}

function notifBody(
  n: AppNotification,
  t: (k: string, v?: Record<string, string | number>) => string,
): string {
  if (n.body?.trim()) return n.body;
  return t(n.bodyKey, n.bodyVars);
}

type Filter = 'all' | 'unread';

export function NotificationsPage() {
  const t = useT();
  const navigate = useNavigate();
  const liveMode = !useMock();
  const [filter, setFilter] = useState<Filter>('all');
  const [mockTick, setMockTick] = useState(0);
  const [liveItems, setLiveItems] = useState<AppNotification[] | null>(null);
  const [loading, setLoading] = useState(liveMode);
  const [error, setError] = useState<string | null>(null);

  const loadLive = useCallback(async () => {
    if (!liveMode) return;
    setLoading(true);
    setError(null);
    try {
      const list = await fetchNotifications();
      setLiveItems(list);
    } catch {
      setLiveItems(null);
      setError(t('notifications.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [liveMode, t]);

  useEffect(() => {
    if (!liveMode) return;
    void loadLive();
  }, [liveMode, loadLive]);

  useEffect(() => {
    if (liveMode) return;
    return subscribeNotifications(() => setMockTick((n) => n + 1));
  }, [liveMode]);

  const [prefsTick, setPrefsTick] = useState(0);
  useEffect(
    () => subscribeNotificationPrefs(() => setPrefsTick((n) => n + 1)),
    [],
  );

  const mockItems = useMemo(() => {
    if (liveMode) return [] as AppNotification[];
    void mockTick;
    return listNotifications();
  }, [liveMode, mockTick]);

  const prefs = prefsTick >= 0 ? loadNotificationPrefs() : loadNotificationPrefs();
  const rawItems = liveMode ? (liveItems ?? []) : mockItems;
  const items = filterNotificationsByPrefs(rawItems, prefs);
  const unread = items.filter((n) => n.unread).length;

  const visible = useMemo(() => {
    if (filter === 'unread') return items.filter((n) => n.unread);
    return items;
  }, [items, filter]);

  const openItem = (n: AppNotification) => {
    if (liveMode && liveItems) {
      setLiveItems(
        liveItems.map((x) => (x.id === n.id ? { ...x, unread: false } : x)),
      );
    }
    void markNotificationRead(n.id).catch(() => {
      /* optimistic UI; reload corrects */
    });
    navigate(n.href);
  };

  const markAll = () => {
    if (liveMode && liveItems) {
      setLiveItems(liveItems.map((n) => ({ ...n, unread: false })));
    }
    void markAllNotificationsRead().catch(() => {
      /* optimistic UI; reload corrects */
    });
    if (!liveMode) setMockTick((n) => n + 1);
  };

  return (
    <section className="page page--notifications">
      <div className="page-head">
        <div>
          <h1>{t('notifications.centerTitle')}</h1>
          <p className="page-subtitle">{t('notifications.centerSubtitle')}</p>
        </div>
        <div className="page-head__meta">
          {unread > 0 && (
            <span className="chip">{t('notifications.unread', { n: unread })}</span>
          )}
          {liveMode && (
            <Button
              type="button"
              variant="secondary"
              onClick={() => void loadLive()}
              disabled={loading}
            >
              <RefreshCw size={14} aria-hidden />
              {t('app.refresh')}
            </Button>
          )}
          {unread > 0 && (
            <Button type="button" variant="secondary" onClick={markAll}>
              {t('notifications.markAllRead')}
            </Button>
          )}
        </div>
      </div>

      <div className="notif-center__filters" role="tablist" aria-label={t('notifications.filterLabel')}>
        <button
          type="button"
          role="tab"
          className={`chip chip--toggle${filter === 'all' ? ' is-on' : ''}`}
          aria-selected={filter === 'all'}
          onClick={() => setFilter('all')}
        >
          {t('notifications.filterAll')}
        </button>
        <button
          type="button"
          role="tab"
          className={`chip chip--toggle${filter === 'unread' ? ' is-on' : ''}`}
          aria-selected={filter === 'unread'}
          onClick={() => setFilter('unread')}
        >
          {t('notifications.filterUnread')}
          {unread > 0 && <b>{unread}</b>}
        </button>
      </div>

      {loading && (
        <div className="notif-center__list" aria-busy="true">
          <Skeleton height={64} />
          <Skeleton height={64} />
          <Skeleton height={64} />
        </div>
      )}

      {!loading && error && (
        <ErrorState
          title={t('notifications.loadFailed')}
          description={t('notifications.loadFailedHint')}
          onRetry={() => void loadLive()}
        />
      )}

      {!loading && !error && visible.length === 0 && (
        <EmptyState
          title={t('notifications.empty')}
          description={
            rawItems.length > 0 && items.length === 0
              ? t('notifications.filteredEmpty')
              : t('notifications.emptyHint')
          }
        />
      )}

      {!loading && !error && visible.length > 0 && (
        <ul className="notif-center__list">
          {visible.map((n) => {
            const Icon = kindIcon[n.kind] ?? Bell;
            return (
              <li key={n.id}>
                <button
                  type="button"
                  className={`notif-center__item${n.unread ? ' is-unread' : ''}`}
                  onClick={() => openItem(n)}
                >
                  <span className={`notif-item__icon notif-item__icon--${n.kind}`}>
                    <Icon size={16} />
                  </span>
                  <span className="notif-center__body">
                    <b>{notifTitle(n, t)}</b>
                    <small>{notifBody(n, t)}</small>
                    <em>
                      <Clock3 size={12} aria-hidden />
                      {formatRelative(n.at, t)}
                    </em>
                  </span>
                  {n.unread && <i className="notif-item__dot" aria-hidden />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
