import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, Clock3, ShieldAlert, UserPlus } from 'lucide-react';
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

const kindIcon: Record<NotificationKind, typeof Bell> = {
  sla: ShieldAlert,
  breach: ShieldAlert,
  assign: UserPlus,
  mention: Bell,
};

function useMockNotifications(): AppNotification[] {
  return useSyncExternalStore(
    subscribeNotifications,
    listNotifications,
    listNotifications,
  );
}

function notifTitle(n: AppNotification, t: (k: string, v?: Record<string, string | number>) => string): string {
  if (n.title?.trim()) return n.title;
  return t(n.titleKey, n.titleVars);
}

function notifBody(n: AppNotification, t: (k: string, v?: Record<string, string | number>) => string): string {
  if (n.body?.trim()) return n.body;
  return t(n.bodyKey, n.bodyVars);
}

export function NotificationMenu() {
  const t = useT();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const mockItems = useMockNotifications();
  const [liveItems, setLiveItems] = useState<AppNotification[] | null>(null);
  const [liveLoaded, setLiveLoaded] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const liveMode = !useMock();
  const items = liveMode && liveLoaded ? (liveItems ?? mockItems) : mockItems;
  const unread = items.filter((n) => n.unread).length;

  useEffect(() => {
    if (!liveMode) {
      setLiveItems(null);
      setLiveLoaded(false);
      return;
    }
    let cancelled = false;
    void fetchNotifications()
      .then((list) => {
        if (cancelled) return;
        setLiveItems(list);
        setLiveLoaded(true);
      })
      .catch(() => {
        if (cancelled) return;
        setLiveItems(null);
        setLiveLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [liveMode]);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const markAllRead = () => {
    if (liveMode && liveItems) {
      setLiveItems(liveItems.map((n) => ({ ...n, unread: false })));
    }
    markAllNotificationsRead();
  };

  const openItem = (n: AppNotification) => {
    if (liveMode && liveItems) {
      setLiveItems(
        liveItems.map((x) => (x.id === n.id ? { ...x, unread: false } : x)),
      );
    }
    markNotificationRead(n.id);
    setOpen(false);
    navigate(n.href);
  };

  return (
    <div className="notif-menu" ref={ref}>
      <button
        type="button"
        className="icon-btn"
        aria-label={t('app.notifications')}
        aria-expanded={open}
        aria-haspopup="true"
        onClick={() => setOpen((v) => !v)}
      >
        <Bell size={19} />
        {unread > 0 && <span className="icon-btn__dot" aria-hidden />}
      </button>

      {open && (
        <div className="dropdown-panel notif-panel" role="menu">
          <div className="dropdown-panel__head">
            <div>
              <b>{t('app.notifications')}</b>
              {unread > 0 && (
                <span className="chip">{t('notifications.unread', { n: unread })}</span>
              )}
            </div>
            <button type="button" className="text-link" onClick={markAllRead}>
              {t('notifications.markAllRead')}
            </button>
          </div>
          {items.length === 0 ? (
            <p className="notif-empty">{t('notifications.empty')}</p>
          ) : (
            <ul className="notif-list">
              {items.map((n) => {
                const Icon = kindIcon[n.kind] ?? Bell;
                return (
                  <li key={n.id}>
                    <button
                      type="button"
                      className={`notif-item${n.unread ? ' is-unread' : ''}`}
                      role="menuitem"
                      onClick={() => openItem(n)}
                    >
                      <span className={`notif-item__icon notif-item__icon--${n.kind}`}>
                        <Icon size={14} />
                      </span>
                      <span className="notif-item__body">
                        <b>{notifTitle(n, t)}</b>
                        <small>{notifBody(n, t)}</small>
                        <em>
                          <Clock3 size={11} aria-hidden />
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
          <div className="dropdown-panel__foot">
            <button
              type="button"
              className="text-link"
              onClick={() => {
                setOpen(false);
                navigate('/my-work');
              }}
            >
              {t('notifications.viewAll')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
